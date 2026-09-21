package org.tinystruct.typesafe.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tinystruct.ApplicationContext;
import org.tinystruct.ApplicationException;
import org.tinystruct.ApplicationRuntimeException;
import org.tinystruct.application.ActionRegistry;
import org.tinystruct.typesafe.core.api.DispatchResult;
import org.tinystruct.typesafe.core.config.TypesafeConfig;
import org.tinystruct.typesafe.core.confirmation.ConfirmationService;
import org.tinystruct.typesafe.core.confirmation.PendingCall;
import org.tinystruct.typesafe.core.testing.MapConfiguration;
import org.tinystruct.typesafe.core.testing.TestApps;

import static org.junit.jupiter.api.Assertions.*;

/** The composition root: what it builds from configuration, and what it refuses to build. */
class TypesafeRuntimeTest {

    @BeforeEach
    void reset() {
        TestApps.Calls.last = null;
        TypesafeRuntime.install(null);
    }

    @AfterEach
    void cleanUp() {
        TypesafeRuntime.install(null);
    }

    // ---- TypesafeRuntime --------------------------------------------------------------------

    private static MapConfiguration config(String... kv) {
        MapConfiguration s = new MapConfiguration();
        for (int i = 0; i < kv.length; i += 2) s.set(kv[i], kv[i + 1]);
        return s;
    }

    @Test
    void buildsARuntimeFromConfigurationAndSharesIt() {
        MapConfiguration config = config(TypesafeConfig.ALLOWED_ACTIONS, "create-user", TypesafeConfig.API_KEY, "k",
                TypesafeConfig.CACHE_PROVIDER, "none");

        TypesafeRuntime first = TypesafeRuntime.shared(config);
        assertNotNull(first.router());
        assertNotNull(first.confirmations());
        assertNotNull(first.confirmedCalls());
        assertNotNull(first.metrics());
        assertSame(first, TypesafeRuntime.shared(config), "built once and shared");
    }

    @Test
    void withoutAnApiKeyRoutingFailsClearlyInsteadOfSendingAFakeKey() {
        MapConfiguration config = config(TypesafeConfig.ALLOWED_ACTIONS, "create-user", TypesafeConfig.API_KEY, "");
        Assumptions.assumeTrue(System.getenv("TYPESAFE_API_KEY") == null);

        TypesafeRuntime runtime = TypesafeRuntime.create(config);

        ApplicationException e = assertThrows(ApplicationException.class,
                () -> runtime.router().route("make John an admin", ActionRegistry.getInstance()));
        assertTrue(e.getMessage().contains("API key"), e.getMessage());
    }

    @Test
    void aConfiguredClassThatCannotBeCreatedStopsStartUp() {
        assertThrows(ApplicationRuntimeException.class, () -> TypesafeRuntime.create(
                config(TypesafeConfig.CONFIRMATION_SERVICE, "no.such.Service")));
        assertThrows(ApplicationRuntimeException.class, () -> TypesafeRuntime.create(
                config(TypesafeConfig.PRINCIPAL_RESOLVER, "java.lang.String")));
    }

    @Test
    void anUnknownCacheProviderStopsStartUp() {
        assertThrows(ApplicationRuntimeException.class,
                () -> TypesafeRuntime.create(config(TypesafeConfig.CACHE_PROVIDER, "carrier-pigeon")));
    }

    /** Public so it can be created by class name, as {@code typesafe.confirmation.service} does. */
    public static class Named implements ConfirmationService {
        static volatile org.tinystruct.system.Configuration<String> configuredWith;

        @Override
        public void configure(org.tinystruct.system.Configuration<String> configuration) {
            configuredWith = configuration;
        }

        @Override
        public String open(PendingCall call) {
            return "n";
        }

        @Override
        public DispatchResult confirm(String pendingId, String principal) {
            return DispatchResult.executed("n");
        }

        @Override
        public void reject(String pendingId, String principal) {}
    }

    @Test
    void aServiceNamedInConfigurationIsCreatedAndConfigured() throws Exception {
        MapConfiguration config = config(TypesafeConfig.CONFIRMATION_SERVICE, Named.class.getName(), TypesafeConfig.CACHE_PROVIDER, "none");

        TypesafeRuntime runtime = TypesafeRuntime.create(config);

        assertSame(config, Named.configuredWith);
        assertEquals(DispatchResult.Status.EXECUTED, runtime.confirmations().confirm("x", new ApplicationContext()).getStatus());
    }
}
