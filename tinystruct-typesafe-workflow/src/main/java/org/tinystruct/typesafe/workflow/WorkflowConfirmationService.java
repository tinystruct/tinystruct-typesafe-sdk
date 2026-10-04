package org.tinystruct.typesafe.workflow;

import org.tinystruct.ApplicationException;
import org.tinystruct.data.component.Builder;
import org.tinystruct.system.Configuration;
import org.tinystruct.typesafe.core.confirmation.ConfirmationConflictException;
import org.tinystruct.typesafe.core.confirmation.ConfirmationExpiredException;
import org.tinystruct.typesafe.core.confirmation.ConfirmationService;
import org.tinystruct.typesafe.core.api.DispatchResult;
import org.tinystruct.typesafe.core.confirmation.PendingCall;
import org.tinystruct.typesafe.core.confirmation.PendingSummary;
import org.tinystruct.typesafe.core.confirmation.PrincipalMismatchException;
import org.tinystruct.workflow.ExecutionContext;
import org.tinystruct.workflow.WorkflowEngine;
import org.tinystruct.workflow.WorkflowException;
import org.tinystruct.workflow.WorkflowStatus;
import org.tinystruct.workflow.repository.SnapshotRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link ConfirmationService} that keeps a pending call as a suspended {@code semantic-confirm}
 * workflow execution, so it survives restarts and is visible to other processes.
 *
 * <p>Confirm and reject verify that the caller is the originator and that the call has not expired,
 * then call the engine directly. They deliberately do not go through {@code EventDispatcher}: the
 * event registry is in memory and would not find a call opened before a restart, and it swallows
 * the errors of the resumed execution.
 *
 * <p>A snapshot holds the call's arguments, so it is deleted as soon as the call is finished.
 */
public class WorkflowConfirmationService implements ConfirmationService {

    private static final Logger LOGGER = Logger.getLogger(WorkflowConfirmationService.class.getName());

    private volatile WorkflowEngine engine;
    private volatile SnapshotRepository repository;
    private volatile boolean keepCompleted;

    /** For creation by class name; {@link #configure} supplies the engine. */
    public WorkflowConfirmationService() {}

    public WorkflowConfirmationService(WorkflowEngine engine, SnapshotRepository repository, boolean keepCompleted) {
        this.engine = engine;
        this.repository = repository;
        this.keepCompleted = keepCompleted;
    }

    @Override
    public void configure(Configuration<String> configuration) {
        this.engine = EngineHolder.getOrCreate(configuration);
        this.repository = EngineHolder.repository();
        this.keepCompleted = EngineHolder.keepCompleted(configuration);
        EngineHolder.startSweeper(configuration, this::sweepExpired);
    }

    @Override
    public String open(PendingCall call) throws ApplicationException {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put(SemanticConfirmApplication.VAR_PENDING_CALL, PendingCallSerializer.toBuilder(call));
        variables.put(SemanticConfirmApplication.VAR_PRINCIPAL, call.getPrincipal());
        variables.put(SemanticConfirmApplication.VAR_EXPIRES_AT, call.getExpiresAt());
        try {
            // Node 'await' runs first and suspends, so the execution is WAITING when this returns.
            String executionId = engine().start(EngineHolder.WORKFLOW_ID, variables);
            LOGGER.info("Opened pending call " + executionId + " for " + call.getActionPath());
            return executionId;
        } catch (WorkflowException e) {
            throw new ApplicationException("Could not hold the call for confirmation: " + e.getMessage(), e);
        }
    }

    @Override
    public DispatchResult confirm(String pendingId, String principal) throws ApplicationException {
        ExecutionContext pending = requireWaiting(pendingId, principal);
        requireNotExpired(pending, pendingId);

        try {
            engine().resume(pendingId, new ConfirmationEvent(pendingId, true));
        } catch (WorkflowException e) {
            throw explain(pendingId, e);
        }

        ExecutionContext finished = load(pendingId);
        DispatchResult result = resultOf(finished);
        finish(pendingId);
        return result;
    }

    @Override
    public void reject(String pendingId, String principal) throws ApplicationException {
        ExecutionContext pending = requireWaiting(pendingId, principal);
        requireNotExpired(pending, pendingId);
        cancel(pendingId);
        finish(pendingId);
        LOGGER.info("Pending call " + pendingId + " rejected.");
    }

    /**
     * The caller's own waiting calls, newest first. Reads every WAITING snapshot and keeps the
     * {@code semantic-confirm} ones whose principal matches, so a caller never learns of another's.
     */
    @Override
    public List<PendingSummary> list(String principal) throws ApplicationException {
        List<PendingSummary> mine = new ArrayList<>();
        for (ExecutionContext waiting : waitingCalls()) {
            String owner = text(waiting, SemanticConfirmApplication.VAR_PRINCIPAL);
            if (owner == null || !owner.equals(principal)) {
                continue;
            }
            PendingSummary summary = summarize(waiting);
            if (summary != null) {
                mine.add(summary);
            }
        }
        mine.sort(Comparator.comparingLong(PendingSummary::createdAt).reversed());
        return mine;
    }

    /**
     * Cancels and deletes every call that expired without an answer.
     *
     * <p>Nothing else does: {@link #confirm} and {@link #reject} only ever reach a call someone
     * came back for, so without this an abandoned one keeps its snapshot — and the arguments in it
     * — for as long as the storage lives.
     *
     * @return how many were discarded
     */
    @Override
    public int sweepExpired() throws ApplicationException {
        long now = System.currentTimeMillis();
        int swept = 0;
        for (ExecutionContext waiting : waitingCalls()) {
            String id = waiting.getExecutionId();
            Object expiresAt = waiting.getVariables().get(SemanticConfirmApplication.VAR_EXPIRES_AT);
            if (expiresAt == null || now <= millis(expiresAt)) {
                continue;
            }
            try {
                cancel(id);
                // An expired call is gone whatever keep-completed says: retention is for calls that
                // were answered, and this one holds arguments nobody is coming back for.
                repository.delete(id);
                swept++;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Could not discard the expired pending call " + id, e);
            }
        }
        if (swept > 0) {
            LOGGER.info("Discarded " + swept + " expired pending call(s).");
        }
        return swept;
    }

    /** Every WAITING {@code semantic-confirm} execution the repository holds. */
    private List<ExecutionContext> waitingCalls() throws ApplicationException {
        if (repository == null) {
            throw new ApplicationException("WorkflowConfirmationService is not configured.");
        }
        List<ExecutionContext> calls = new ArrayList<>();
        try {
            for (ExecutionContext context : repository.findByStatus(WorkflowStatus.WAITING)) {
                if (EngineHolder.WORKFLOW_ID.equals(context.getWorkflowId())) {
                    calls.add(context);
                }
            }
        } catch (UnsupportedOperationException e) {
            throw new ApplicationException("The configured " + EngineHolder.REPOSITORY
                    + " cannot list pending calls. Use 'file', 'redis' or 'database'.", e);
        } catch (WorkflowException e) {
            throw new ApplicationException("Could not read the pending calls: " + e.getMessage(), e);
        }
        return calls;
    }

    /** A snapshot written by an older version, or a corrupted one, is skipped rather than fatal. */
    private PendingSummary summarize(ExecutionContext waiting) {
        try {
            Object stored = waiting.getVariables().get(SemanticConfirmApplication.VAR_PENDING_CALL);
            if (!(stored instanceof Builder call)) {
                return null;
            }
            PendingCallSerializer.StoredCall pending = PendingCallSerializer.fromBuilder(call);
            return new PendingSummary(waiting.getExecutionId(), pending.actionPath(), pending.principal(),
                    pending.confidence(), pending.createdAt(), pending.expiresAt());
        } catch (Exception e) {
            LOGGER.warning("Skipping an unreadable pending call: " + waiting.getExecutionId());
            return null;
        }
    }

    private static String text(ExecutionContext context, String variable) {
        Object value = context.getVariables().get(variable);
        return value == null ? null : value.toString();
    }

    private static long millis(Object value) {
        try {
            return new java.math.BigDecimal(value.toString()).longValue();
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE; // unreadable expiry: leave it alone rather than discard it
        }
    }

    // ---- checks -----------------------------------------------------------------------------

    private ExecutionContext requireWaiting(String pendingId, String principal) throws ApplicationException {
        ExecutionContext context = load(pendingId);
        if (!EngineHolder.WORKFLOW_ID.equals(context.getWorkflowId())) {
            throw new ApplicationException("Pending call not found: " + pendingId);
        }
        Object owner = context.getVariables().get(SemanticConfirmApplication.VAR_PRINCIPAL);
        if (owner == null || !owner.toString().equals(principal)) {
            LOGGER.warning("Refused: caller is not the originator of pending call " + pendingId);
            throw new PrincipalMismatchException(pendingId);
        }
        if (context.getStatus() != WorkflowStatus.WAITING) {
            throw new ConfirmationConflictException(pendingId);
        }
        return context;
    }

    private void requireNotExpired(ExecutionContext context, String pendingId) throws ApplicationException {
        Object expiresAt = context.getVariables().get(SemanticConfirmApplication.VAR_EXPIRES_AT);
        if (expiresAt != null && System.currentTimeMillis() > new java.math.BigDecimal(expiresAt.toString()).longValue()) {
            cancel(pendingId);
            finish(pendingId);
            throw new ConfirmationExpiredException(pendingId);
        }
    }

    /**
     * {@code resume} throws for two different reasons: another caller got there first, or the
     * confirmed call itself failed. The stored status tells them apart.
     */
    private ApplicationException explain(String pendingId, WorkflowException resumeFailure) {
        try {
            ExecutionContext after = load(pendingId);
            if (after.getStatus() == WorkflowStatus.FAILED) {
                String reason = after.getFailureMessage();
                finish(pendingId);
                return new ApplicationException("The confirmed call failed: " + reason, resumeFailure);
            }
        } catch (ApplicationException ignored) {
            // the snapshot is gone: someone else already finished it
        }
        return new ConfirmationConflictException(pendingId);
    }

    private DispatchResult resultOf(ExecutionContext finished) throws ApplicationException {
        if (finished.getStatus() != WorkflowStatus.COMPLETED) {
            throw new ApplicationException("The confirmed call did not complete (status "
                    + finished.getStatus() + ").");
        }
        Object outcome = finished.getVariables().get(SemanticConfirmApplication.VAR_DISPATCH_RESULT);
        if (outcome instanceof Builder b && b.get("result") != null) {
            return DispatchResult.executed(b.get("result").toString());
        }
        return DispatchResult.executed(null);
    }

    // ---- engine and storage -----------------------------------------------------------------

    private ExecutionContext load(String pendingId) throws ApplicationException {
        try {
            return engine().getExecution(pendingId);
        } catch (WorkflowException e) {
            throw new ApplicationException("Pending call not found: " + pendingId, e);
        }
    }

    /** {@code engine.cancel} does not check the status, so it must only be called on a WAITING call. */
    private void cancel(String pendingId) throws ApplicationException {
        try {
            if (engine().getExecution(pendingId).getStatus() == WorkflowStatus.WAITING) {
                engine().cancel(pendingId);
            }
        } catch (WorkflowException e) {
            LOGGER.warning("Could not cancel " + pendingId + ": " + e.getMessage());
        }
    }

    /** Removes the snapshot (it holds arguments), unless audit retention is switched on. */
    private void finish(String pendingId) {
        if (keepCompleted || repository == null) return;
        try {
            repository.delete(pendingId);
        } catch (WorkflowException e) {
            LOGGER.warning("Could not delete the snapshot of " + pendingId + ": " + e.getMessage());
        }
    }

    private WorkflowEngine engine() throws ApplicationException {
        WorkflowEngine current = engine;
        if (current == null) {
            throw new ApplicationException("WorkflowConfirmationService is not configured.");
        }
        return current;
    }
}
