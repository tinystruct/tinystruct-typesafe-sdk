package org.tinystruct.typesafe.workflow;

import org.tinystruct.ApplicationException;
import org.tinystruct.data.component.Builder;
import org.tinystruct.typesafe.core.argument.ArgumentCodec;
import org.tinystruct.typesafe.core.confirmation.PendingCall;

/**
 * Stores a {@link PendingCall} as a {@link Builder} workflow variable and reads it back.
 *
 * <p>Arguments are stored with {@link ArgumentCodec} so their types survive the snapshot; they are
 * decoded against the action's current signature only when the call is executed.
 */
public final class PendingCallSerializer {

    /** The persisted form of a pending call. Its arguments are still encoded. */
    public record StoredCall(String actionPath, String mode, Builder encodedArguments,
                             String principal, double confidence, long createdAt, long expiresAt) {}

    private PendingCallSerializer() {}

    public static Builder toBuilder(PendingCall call) {
        Builder b = new Builder();
        b.put("actionPath", call.getActionPath());
        b.put("mode", call.getMode());
        b.put("principal", call.getPrincipal());
        b.put("confidence", call.getConfidence());
        b.put("createdAt", call.getCreatedAt());
        b.put("expiresAt", call.getExpiresAt());
        b.put("arguments", ArgumentCodec.encode(call.getResolvedArguments()));
        return b;
    }

    public static StoredCall fromBuilder(Builder b) throws ApplicationException {
        Object arguments = b.get("arguments");
        Builder encoded = new Builder();
        if (arguments instanceof Builder ab) {
            encoded = ab;
        } else if (arguments != null) {
            encoded.parse(arguments.toString());
        }
        return new StoredCall(
                text(b, "actionPath"),
                b.containsKey("mode") ? text(b, "mode") : "DEFAULT",
                encoded,
                text(b, "principal"),
                Double.parseDouble(text(b, "confidence")),
                integer(b, "createdAt"),
                integer(b, "expiresAt"));
    }

    private static String text(Builder b, String key) throws ApplicationException {
        Object value = b.get(key);
        if (value == null) throw new ApplicationException("Stored pending call has no '" + key + "'.");
        return value.toString();
    }

    private static long integer(Builder b, String key) throws ApplicationException {
        try {
            return new java.math.BigDecimal(text(b, key)).longValue();
        } catch (NumberFormatException e) {
            throw new ApplicationException("Stored pending call has an invalid '" + key + "'.", e);
        }
    }
}
