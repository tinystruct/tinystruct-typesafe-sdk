package org.tinystruct.typesafe.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tinystruct.ApplicationContext;
import org.tinystruct.ApplicationException;
import org.tinystruct.application.Context;
import org.tinystruct.data.component.Builder;
import org.tinystruct.typesafe.core.api.DispatchResult;
import org.tinystruct.typesafe.core.testing.MockClientRuntime;
import org.tinystruct.typesafe.core.testing.TestApps;

import static org.junit.jupiter.api.Assertions.*;

/** The tinystruct entry point: reading --input, and the JSON it returns. */
class SemanticDispatcherTest {

    @BeforeAll
    static void install() {
        TestApps.install();
    }

    @BeforeEach
    void reset() {
        TestApps.Calls.last = null;
        TypesafeRuntime.install(null);
    }

    @AfterEach
    void cleanUp() {
        TypesafeRuntime.install(null);
    }

    // ---- SemanticDispatcher -----------------------------------------------------------------

    private static SemanticDispatcher dispatcherWith(Context context) {
        SemanticDispatcher dispatcher = new SemanticDispatcher();
        dispatcher.setContext(context);
        return dispatcher;
    }

    @Test
    void semanticReadsTheInputOption() throws Exception {
        MockClientRuntime.installReturning("create-user", "John", "ADMIN");
        Context context = new ApplicationContext();
        context.setAttribute("--input", "make John an admin");

        Builder out = dispatcherWith(context).semantic();

        assertEquals("EXECUTED", out.get("status").toString());
        assertEquals("create-user:John:ADMIN", out.get("result").toString());
    }

    @Test
    void semanticWithoutInputExplainsHowToCallIt() {
        ApplicationException e = assertThrows(ApplicationException.class,
                () -> dispatcherWith(new ApplicationContext()).semantic());
        assertTrue(e.getMessage().contains("--input"));
    }

    @Test
    void metricsActionReturnsJson() throws Exception {
        MockClientRuntime.installReturning("create-user", "John", "ADMIN");
        Builder out = dispatcherWith(new ApplicationContext()).metrics();
        assertTrue(out.containsKey("totalRoutings"));
    }

    @Test
    void confirmAndRejectFailWithoutAConfirmationService() {
        MockClientRuntime.installReturning("create-user", "John", "ADMIN");
        SemanticDispatcher dispatcher = dispatcherWith(new ApplicationContext());
        assertThrows(ApplicationException.class, () -> dispatcher.confirm("abc"));
        assertThrows(ApplicationException.class, () -> dispatcher.reject("abc"));
    }

    @Test
    void formatIncludesOnlyThePartsThatExist() {
        Builder pending = SemanticDispatcher.format(DispatchResult.needsConfirmation("id-1"));
        assertEquals("id-1", pending.get("pendingId").toString());
        assertFalse(pending.containsKey("reason"));

        Builder rejected = SemanticDispatcher.format(DispatchResult.rejected("because"));
        assertEquals("because", rejected.get("reason").toString());
        assertFalse(rejected.containsKey("result"));
    }
}
