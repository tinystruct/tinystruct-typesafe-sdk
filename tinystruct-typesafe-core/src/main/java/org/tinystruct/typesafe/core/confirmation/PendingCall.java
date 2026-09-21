package org.tinystruct.typesafe.core.confirmation;

import org.tinystruct.typesafe.core.argument.ArgumentCodec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A fully validated call that is waiting for a human to confirm it.
 *
 * <p>Holds the resolved (typed) arguments, the principal that originated the call, the caller's
 * {@code @Action} mode at that moment, and an expiry. Persisting and restoring the typed
 * arguments is done with {@link ArgumentCodec}.
 */
public final class PendingCall {

    private final String actionPath;
    private final Map<String, Object> resolvedArguments;
    private final String principal;
    private final double confidence;
    private final long createdAt;
    private final long expiresAt;
    private final String mode;

    public PendingCall(String actionPath, Map<String, Object> resolvedArguments,
                       String principal, double confidence, long createdAt, long expiresAt, String mode) {
        this.actionPath = actionPath;
        this.resolvedArguments = Collections.unmodifiableMap(new LinkedHashMap<>(resolvedArguments));
        this.principal = principal;
        this.confidence = confidence;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.mode = mode == null ? "DEFAULT" : mode;
    }

    public PendingCall(String actionPath, Map<String, Object> resolvedArguments,
                       String principal, double confidence, long createdAt, long expiresAt) {
        this(actionPath, resolvedArguments, principal, confidence, createdAt, expiresAt, "DEFAULT");
    }

    public String getActionPath() { return actionPath; }
    public Map<String, Object> getResolvedArguments() { return resolvedArguments; }
    public String getPrincipal() { return principal; }
    public double getConfidence() { return confidence; }
    public long getCreatedAt() { return createdAt; }
    public long getExpiresAt() { return expiresAt; }

    /** Name of the {@code @Action.Mode} the originating caller was running in. */
    public String getMode() { return mode; }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }

    /** Never includes argument values: they may be personal data. */
    @Override
    public String toString() {
        return "PendingCall{action=" + actionPath + ", principal=" + principal + "}";
    }
}
