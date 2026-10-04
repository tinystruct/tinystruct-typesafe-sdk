package org.tinystruct.typesafe.core.confirmation;

import org.tinystruct.data.component.Builder;

/**
 * What can be said about a call that is waiting for confirmation, without saying what it would do
 * to which row.
 *
 * <p>Deliberately no arguments: the values are the part most likely to be personal data, and a
 * listing is the one place they would be shown to someone who is only browsing. Whoever wants to
 * know what a call will do confirms it, or reads the snapshot with the operator's own tools.
 *
 * @param pendingId  the id to confirm or reject with
 * @param actionPath the action that would run
 * @param principal  who opened it, and the only one who may answer it
 * @param confidence the routing confidence recorded when it was opened
 * @param createdAt  epoch millis
 * @param expiresAt  epoch millis after which it can no longer be confirmed
 */
public record PendingSummary(String pendingId, String actionPath, String principal,
                             double confidence, long createdAt, long expiresAt) {

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    /** Milliseconds until it expires; negative once it has. */
    public long remainingMillis() {
        return expiresAt - System.currentTimeMillis();
    }

    public Builder toBuilder() {
        Builder b = new Builder();
        b.put("pendingId", pendingId);
        b.put("actionPath", actionPath);
        b.put("principal", principal);
        b.put("confidence", confidence);
        b.put("createdAt", createdAt);
        b.put("expiresAt", expiresAt);
        b.put("expired", isExpired());
        return b;
    }
}
