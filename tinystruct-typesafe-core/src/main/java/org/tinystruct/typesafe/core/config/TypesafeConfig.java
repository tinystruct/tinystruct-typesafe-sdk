package org.tinystruct.typesafe.core.config;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The {@code application.properties} keys this extension reads, and typed parsing of their values.
 *
 * <p>tinystruct exposes configuration as plain strings ({@code getConfiguration(key)}), so the
 * typed getters live here once rather than being repeated in every class.
 */
public final class TypesafeConfig {

    // Routing
    public static final String ALLOWED_ACTIONS = "typesafe.routing.allowed-actions";
    public static final String CONFIRM_ACTIONS = "typesafe.routing.confirm-actions";
    public static final String MIN_CONFIDENCE = "typesafe.routing.min-confidence";
    /** Prefix of per-action overrides: {@code typesafe.routing.min-confidence.<action>}. */
    public static final String MIN_CONFIDENCE_PREFIX = MIN_CONFIDENCE + ".";
    public static final String AUTO_CONFIDENCE = "typesafe.routing.auto-confidence";
    public static final String SET_THRESHOLD = "typesafe.routing.set-threshold";
    public static final String STRATEGY = "typesafe.routing.strategy";
    public static final String CONFIRMATION_TIMEOUT = "typesafe.routing.confirmation-timeout-seconds";
    public static final String MAX_ARGUMENT_LENGTH = "typesafe.validation.max-argument-length";

    // Pluggable implementations, selected by class name like tinystruct's default.session.repository
    public static final String CONFIRMATION_SERVICE = "typesafe.confirmation.service";
    public static final String PRINCIPAL_RESOLVER = "typesafe.principal.resolver";

    // TypeSafe client
    public static final String API_KEY = "typesafe.api-key";
    public static final String ENDPOINT = "typesafe.endpoint";
    public static final String MODEL = "typesafe.model";
    public static final String CONNECT_TIMEOUT_MS = "typesafe.connect-timeout-ms";
    public static final String READ_TIMEOUT_MS = "typesafe.read-timeout-ms";
    public static final String RETRY_MAX = "typesafe.retry-max";
    public static final String RETRY_BACKOFF_MS = "typesafe.retry-backoff-ms";

    // Cache and logging
    public static final String CACHE_PROVIDER = "typesafe.cache.provider";
    public static final String CACHE_TTL = "typesafe.cache.ttl";
    public static final String LOG_ARGUMENTS = "typesafe.logging.log-arguments";

    private TypesafeConfig() {}

    /** Splits a comma-separated value into an ordered, trimmed, de-duplicated set. */
    public static Set<String> csv(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (value == null || value.isBlank()) return result;
        for (String s : value.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    public static double dbl(String value, double defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static int integer(String value, int defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static boolean bool(String value, boolean defaultValue) {
        if (value == null || value.isBlank()) return defaultValue;
        return "true".equalsIgnoreCase(value.trim());
    }
}
