package org.tinystruct.typesafe.core.config;

import org.junit.jupiter.api.Test;
import org.tinystruct.typesafe.core.argument.ArgumentValidator;
import org.tinystruct.typesafe.core.config.RoutingSettings;
import org.tinystruct.typesafe.core.config.TypesafeConfig;
import org.tinystruct.typesafe.core.testing.MapConfiguration;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Settings are read from configuration with safe defaults. */
class RoutingSettingsTest {

    // ---- TypesafeRuntime --------------------------------------------------------------------

    private static MapConfiguration config(String... kv) {
        MapConfiguration s = new MapConfiguration();
        for (int i = 0; i < kv.length; i += 2) s.set(kv[i], kv[i + 1]);
        return s;
    }

    @Test
    void settingsFallBackToSafeDefaults() {
        RoutingSettings s = RoutingSettings.from(new MapConfiguration());

        assertTrue(s.allowedActions().isEmpty(), "nothing is routable until allowlisted");
        assertTrue(s.confirmActions().isEmpty());
        assertEquals(RoutingSettings.Strategy.SINGLE, s.strategy());
        assertEquals("jev-latest", s.model());
        assertFalse(s.logArguments());
        assertEquals(300, s.confirmationTimeoutSeconds());
        assertEquals(ArgumentValidator.DEFAULT_MAX_LENGTH, s.maxArgumentLength());
    }

    @Test
    void settingsReadEveryConfiguredValue() {
        RoutingSettings s = RoutingSettings.from(config(
                TypesafeConfig.ALLOWED_ACTIONS, " create-user , delete-user ",
                TypesafeConfig.CONFIRM_ACTIONS, "delete-user",
                TypesafeConfig.STRATEGY, "two-stage",
                TypesafeConfig.MODEL, "jev-1.13.0",
                TypesafeConfig.LOG_ARGUMENTS, "true",
                TypesafeConfig.CONFIRMATION_TIMEOUT, "60",
                TypesafeConfig.MAX_ARGUMENT_LENGTH, "50"));

        assertEquals(Set.of("create-user", "delete-user"), s.allowedActions());
        assertEquals(Set.of("delete-user"), s.confirmActions());
        assertEquals(RoutingSettings.Strategy.TWO_STAGE, s.strategy());
        assertEquals("jev-1.13.0", s.model());
        assertTrue(s.logArguments());
        assertEquals(60, s.confirmationTimeoutSeconds());
        assertEquals(50, s.maxArgumentLength());
    }

    @Test
    void typedConfigHelpersFallBackOnBadInput() {
        assertEquals(7, TypesafeConfig.integer("x", 7));
        assertEquals(7, TypesafeConfig.integer(null, 7));
        assertEquals(1.5, TypesafeConfig.dbl("nope", 1.5));
        assertTrue(TypesafeConfig.bool(" TRUE ", false));
        assertFalse(TypesafeConfig.bool("yes", false));
        assertEquals(List.of("a", "b"), List.copyOf(TypesafeConfig.csv("a, b,,a")));
    }
}
