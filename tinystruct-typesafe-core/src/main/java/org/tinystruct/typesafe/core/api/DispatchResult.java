package org.tinystruct.typesafe.core.api;

/**
 * The outcome of a semantic dispatch call.
 *
 * <ul>
 *   <li>{@link Status#EXECUTED} – the action ran and {@link #result} holds its return value.</li>
 *   <li>{@link Status#NEEDS_CONFIRMATION} – the call is pending; {@link #pendingId} identifies it.</li>
 *   <li>{@link Status#REJECTED} – the call was refused (low confidence, invalid, fail-closed, etc.).
 *       {@link #reason} explains why.</li>
 * </ul>
 */
public final class DispatchResult {

    public enum Status {
        EXECUTED,
        NEEDS_CONFIRMATION,
        REJECTED
    }

    private final Status status;
    private final Object result;
    private final String pendingId;
    private final String reason;

    private DispatchResult(Status status, Object result, String pendingId, String reason) {
        this.status = status;
        this.result = result;
        this.pendingId = pendingId;
        this.reason = reason;
    }

    public static DispatchResult executed(Object result) {
        return new DispatchResult(Status.EXECUTED, result, null, null);
    }

    public static DispatchResult needsConfirmation(String pendingId) {
        return new DispatchResult(Status.NEEDS_CONFIRMATION, null, pendingId, null);
    }

    public static DispatchResult rejected(String reason) {
        return new DispatchResult(Status.REJECTED, null, null, reason);
    }

    public Status getStatus() {
        return status;
    }

    /** The return value from the target @Action method, or {@code null} for non-EXECUTED results. */
    public Object getResult() {
        return result;
    }

    /** The pending call identifier for {@link Status#NEEDS_CONFIRMATION} results. */
    public String getPendingId() {
        return pendingId;
    }

    /** Human-readable explanation for {@link Status#REJECTED} results. */
    public String getReason() {
        return reason;
    }

    @Override
    public String toString() {
        return "DispatchResult{status=" + status
                + (pendingId != null ? ", pendingId=" + pendingId : "")
                + (reason != null ? ", reason=" + reason : "")
                + (result != null ? ", result=" + result : "")
                + "}";
    }
}
