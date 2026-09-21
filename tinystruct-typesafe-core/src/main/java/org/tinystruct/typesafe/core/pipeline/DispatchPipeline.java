package org.tinystruct.typesafe.core.pipeline;

import org.tinystruct.ApplicationException;
import org.tinystruct.application.ActionRegistry;
import org.tinystruct.application.Context;
import org.tinystruct.system.annotation.Action.Mode;
import org.tinystruct.typesafe.client.TypesafeClient;
import org.tinystruct.typesafe.core.api.ActionSemanticRouter;
import org.tinystruct.typesafe.core.api.DispatchResult;
import org.tinystruct.typesafe.core.argument.ArgumentResolver;
import org.tinystruct.typesafe.core.argument.ArgumentValidator;
import org.tinystruct.typesafe.core.candidate.CandidateExtractor;
import org.tinystruct.typesafe.core.catalog.ActionCatalog;
import org.tinystruct.typesafe.core.catalog.ActionDefinition;
import org.tinystruct.typesafe.core.catalog.CallerMode;
import org.tinystruct.typesafe.core.config.RoutingSettings;
import org.tinystruct.typesafe.core.confirmation.ConfirmationService;
import org.tinystruct.typesafe.core.confirmation.PendingCall;
import org.tinystruct.typesafe.core.confirmation.PrincipalResolver;
import org.tinystruct.typesafe.core.execution.ActionExecutor;
import org.tinystruct.typesafe.core.metrics.DispatchMetrics;
import org.tinystruct.typesafe.core.policy.AmbiguousIntentException;
import org.tinystruct.typesafe.core.policy.ConfidencePolicy;
import org.tinystruct.typesafe.core.policy.ConfidenceScore;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Decides what to do with an {@link Interpretation}, and does it: refuse, hold for confirmation, or
 * execute. Understanding the instruction is the {@link Interpreter}'s job; this class owns the
 * policy and the side effects.
 *
 * <p>It is plain synchronous code that owns no I/O of its own. Everything it needs arrives through
 * its constructor as an abstraction, so it runs the same in a test as in production. Caching and
 * metering of TypeSafe calls are decorators around the client, not concerns of this class.
 */
public final class DispatchPipeline implements ActionSemanticRouter {

    private static final Logger LOGGER = Logger.getLogger(DispatchPipeline.class.getName());

    private final Interpreter interpreter;
    private final ConfidencePolicy policy;
    private final ActionExecutor executor;
    private final ConfirmationService confirmation; // null: confirmation is not configured, so it fails closed
    private final PrincipalResolver principals;
    private final RoutingSettings settings;
    private final DispatchMetrics metrics;

    public DispatchPipeline(Interpreter interpreter, ConfidencePolicy policy, ActionExecutor executor,
                            ConfirmationService confirmation, PrincipalResolver principals,
                            RoutingSettings settings, DispatchMetrics metrics) {
        this.interpreter = interpreter;
        this.policy = policy;
        this.executor = executor;
        this.confirmation = confirmation;
        this.principals = principals;
        this.settings = settings;
        this.metrics = metrics;
    }

    /**
     * Assembles the standard pipeline: the classification strategy comes from
     * {@link RoutingSettings#strategy()}, and the interpreter is built around it.
     */
    public static DispatchPipeline of(TypesafeClient client, CandidateExtractor extractor, ConfidencePolicy policy,
                                      ArgumentValidator validator, ActionExecutor executor,
                                      ConfirmationService confirmation, PrincipalResolver principals,
                                      RoutingSettings settings, DispatchMetrics metrics) {
        Classifier classifier = settings.strategy() == RoutingSettings.Strategy.TWO_STAGE
                ? new TwoStageClassifier(client, extractor, settings.model())
                : new SingleStageClassifier(client, extractor, settings.model());
        Interpreter interpreter = new Interpreter(new ActionCatalog(settings.allowedActions()), classifier,
                new ArgumentResolver(policy.getSetThreshold(), extractor), validator, policy);
        return new DispatchPipeline(interpreter, policy, executor, confirmation, principals, settings, metrics);
    }

    @Override
    public DispatchResult route(String input, ActionRegistry registry, Context context) throws ApplicationException {
        long started = System.currentTimeMillis();
        try {
            DispatchResult result = dispatch(input, registry, context);
            metrics.recordRouting(result, System.currentTimeMillis() - started);
            return result;
        } catch (AmbiguousIntentException e) {
            metrics.recordAmbiguous(System.currentTimeMillis() - started);
            throw e;
        }
    }

    private DispatchResult dispatch(String input, ActionRegistry registry, Context context)
            throws ApplicationException {
        Mode mode = CallerMode.of(context);
        Interpretation interpretation = interpreter.interpret(input, registry, mode);
        if (interpretation.action() != null) {
            metrics.recordAction(interpretation.action().getActionPath());
        }
        if (interpretation.isRefused()) {
            return DispatchResult.rejected(interpretation.refusal());
        }

        ActionDefinition action = interpretation.action();
        ConfidenceScore score = interpretation.score();
        metrics.recordConfidence(score.getValue());
        LOGGER.info("Routing " + action.getActionPath() + ", " + score);

        ConfidencePolicy.Tier tier = policy.classify(score, action.getActionPath());
        if (tier == ConfidencePolicy.Tier.AMBIGUOUS) {
            throw new AmbiguousIntentException(score.getValue(), policy.minConfidenceFor(action.getActionPath()));
        }

        boolean confirmationRequired = tier == ConfidencePolicy.Tier.CONFIRM
                || settings.confirmActions().contains(action.getActionPath());
        if (confirmationRequired) {
            return requestConfirmation(action, interpretation.arguments(), score, context, mode);
        }
        return execute(action, interpretation.arguments(), context, mode);
    }

    private DispatchResult requestConfirmation(ActionDefinition action, Map<String, Object> arguments,
                                               ConfidenceScore score, Context context, Mode mode)
            throws ApplicationException {
        if (confirmation == null) {
            LOGGER.severe("'" + action.getActionPath() + "' needs confirmation but no ConfirmationService is "
                    + "configured. Failing closed.");
            return DispatchResult.rejected("The action needs confirmation, and confirmation is not configured.");
        }
        long now = System.currentTimeMillis();
        PendingCall call = new PendingCall(action.getActionPath(), arguments, principals.resolve(context),
                score.getValue(), now, now + settings.confirmationTimeoutSeconds() * 1000L, mode.name());
        return DispatchResult.needsConfirmation(confirmation.open(call));
    }

    private DispatchResult execute(ActionDefinition action, Map<String, Object> arguments, Context context, Mode mode)
            throws ApplicationException {
        Object result = executor.execute(action, arguments, context, mode);
        LOGGER.info("Executed " + action.getActionPath()
                + (settings.logArguments() ? " " + arguments : " [arguments redacted]"));
        return DispatchResult.executed(result);
    }
}
