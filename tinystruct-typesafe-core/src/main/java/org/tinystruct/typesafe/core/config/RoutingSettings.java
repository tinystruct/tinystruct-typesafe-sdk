package org.tinystruct.typesafe.core.config;

import org.tinystruct.system.Configuration;
import org.tinystruct.typesafe.client.HttpTypesafeClient;
import org.tinystruct.typesafe.core.argument.ArgumentValidator;

import java.util.Set;

/**
 * The routing configuration the pipeline needs, read once from {@code application.properties}.
 *
 * <p>Keeping it a plain immutable value means the pipeline never touches configuration itself.
 */
public record RoutingSettings(
        Set<String> allowedActions,
        Set<String> confirmActions,
        Strategy strategy,
        String model,
        boolean logArguments,
        int confirmationTimeoutSeconds,
        int maxArgumentLength) {

    /** How questions are sent to TypeSafe. */
    public enum Strategy {
        /** Every question in one request (the documented pattern). */
        SINGLE,
        /** Only {@code __tool__} first, then just the chosen action's questions. Cheaper with many actions. */
        TWO_STAGE;

        static Strategy parse(String value) {
            if (value == null) return SINGLE;
            return switch (value.trim().toLowerCase()) {
                case "two-stage", "two_stage", "twostage" -> TWO_STAGE;
                default -> SINGLE;
            };
        }
    }

    public static RoutingSettings from(Configuration<String> config) {
        String model = config.get(TypesafeConfig.MODEL);
        return new RoutingSettings(
                TypesafeConfig.csv(config.get(TypesafeConfig.ALLOWED_ACTIONS)),
                TypesafeConfig.csv(config.get(TypesafeConfig.CONFIRM_ACTIONS)),
                Strategy.parse(config.get(TypesafeConfig.STRATEGY)),
                model == null || model.isBlank() ? HttpTypesafeClient.DEFAULT_MODEL : model.trim(),
                TypesafeConfig.bool(config.get(TypesafeConfig.LOG_ARGUMENTS), false),
                TypesafeConfig.integer(config.get(TypesafeConfig.CONFIRMATION_TIMEOUT), 300),
                TypesafeConfig.integer(config.get(TypesafeConfig.MAX_ARGUMENT_LENGTH), ArgumentValidator.DEFAULT_MAX_LENGTH));
    }
}
