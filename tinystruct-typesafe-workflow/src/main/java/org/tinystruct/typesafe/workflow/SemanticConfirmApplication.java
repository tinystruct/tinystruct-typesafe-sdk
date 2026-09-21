package org.tinystruct.typesafe.workflow;

import org.tinystruct.AbstractApplication;
import org.tinystruct.ApplicationException;
import org.tinystruct.application.ActionRegistry;
import org.tinystruct.data.component.Builder;
import org.tinystruct.system.ApplicationManager;
import org.tinystruct.system.annotation.Action;
import org.tinystruct.typesafe.core.api.DispatchResult;
import org.tinystruct.typesafe.core.TypesafeRuntime;
import org.tinystruct.workflow.Workflow;

import java.util.logging.Logger;

/**
 * The two nodes of the {@code semantic-confirm} workflow.
 *
 * <p>Nodes are ordinary public {@code @Action}s, so they are routable from the command line or HTTP
 * like any other. Each therefore refuses to run unless the workflow engine is driving it, which it
 * can tell from the workflow variables that only exist during an execution.
 *
 * <p>Node {@code execute} does not run the action itself: it hands the stored call to the core's
 * {@code ConfirmedCallRunner}, which re-checks everything that may have changed while the call was
 * waiting.
 */
public class SemanticConfirmApplication extends AbstractApplication {

    private static final Logger LOGGER = Logger.getLogger(SemanticConfirmApplication.class.getName());

    /** Workflow variables. */
    static final String VAR_PENDING_CALL = "__pendingCall__";
    static final String VAR_PRINCIPAL = "__principal__";
    static final String VAR_EXPIRES_AT = "__expiresAt__";
    static final String VAR_DISPATCH_RESULT = "__dispatchResult__";
    static final String VAR_RESUME_PAYLOAD = "__resumePayload";

    @Override
    public void init() {
        setTemplateRequired(false);
        EngineHolder.getOrCreate(configuration()); // idempotent; also re-registers the definition after a restart
    }

    /** The application's configuration, or the framework's own if it was not given one. */
    private org.tinystruct.system.Configuration<String> configuration() {
        return getConfiguration() != null ? getConfiguration() : ApplicationManager.getConfiguration();
    }

    @Override
    public String version() {
        return "1.0.0-SNAPSHOT";
    }

    /** Node 0: suspends until the call is confirmed. */
    @Action(value = "semantic-confirm/await",
            description = "Workflow node: waits for a confirmation. Not callable directly.")
    public void await() throws ApplicationException {
        requireWorkflow();
        Workflow.suspend("Waiting for human confirmation", ConfirmationEvent.class);
    }

    /** Node 1: runs after confirmation. */
    @Action(value = "semantic-confirm/execute",
            description = "Workflow node: runs the confirmed call. Not callable directly.")
    public void execute() throws ApplicationException {
        requireWorkflow();

        if (!Boolean.TRUE.equals(Workflow.getVariable(VAR_RESUME_PAYLOAD))) {
            throw new ApplicationException("The call was resumed without a positive confirmation.");
        }
        Object stored = Workflow.getVariable(VAR_PENDING_CALL);
        if (!(stored instanceof Builder call)) {
            throw new ApplicationException("The stored call is missing or malformed.");
        }
        PendingCallSerializer.StoredCall pending = PendingCallSerializer.fromBuilder(call);
        if (System.currentTimeMillis() > pending.expiresAt()) {
            throw new ApplicationException("The call expired before it could run.");
        }

        Object result = TypesafeRuntime.shared(configuration()).confirmedCalls().run(
                ActionRegistry.getInstance(), getContext(), pending.actionPath(), pending.mode(),
                pending.encodedArguments());

        Builder outcome = new Builder();
        outcome.put("status", DispatchResult.Status.EXECUTED.name());
        if (result != null) outcome.put("result", result.toString());
        Workflow.setVariable(VAR_DISPATCH_RESULT, outcome);
        LOGGER.info("Confirmed call executed: " + pending.actionPath());
    }

    private static void requireWorkflow() throws ApplicationException {
        if (Workflow.getVariable(VAR_PENDING_CALL) == null) {
            throw new ApplicationException("This is a workflow node and cannot be called directly.");
        }
    }
}
