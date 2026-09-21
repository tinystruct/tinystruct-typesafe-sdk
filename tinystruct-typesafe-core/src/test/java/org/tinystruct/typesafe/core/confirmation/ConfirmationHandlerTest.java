package org.tinystruct.typesafe.core.confirmation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tinystruct.ApplicationContext;
import org.tinystruct.ApplicationException;
import org.tinystruct.application.ActionRegistry;
import org.tinystruct.data.component.Builder;
import org.tinystruct.system.annotation.Action.Mode;
import org.tinystruct.typesafe.core.api.DispatchResult;
import org.tinystruct.typesafe.core.TypesafeRuntime;
import org.tinystruct.typesafe.core.argument.ArgumentCodec;
import org.tinystruct.typesafe.core.argument.ArgumentValidator;
import org.tinystruct.typesafe.core.argument.InvalidActionException;
import org.tinystruct.typesafe.core.catalog.ActionCatalog;
import org.tinystruct.typesafe.core.confirmation.ConfirmationConflictException;
import org.tinystruct.typesafe.core.confirmation.ConfirmationExpiredException;
import org.tinystruct.typesafe.core.confirmation.ConfirmationHandler;
import org.tinystruct.typesafe.core.confirmation.ConfirmationService;
import org.tinystruct.typesafe.core.confirmation.ConfirmedCallRunner;
import org.tinystruct.typesafe.core.confirmation.PendingCall;
import org.tinystruct.typesafe.core.confirmation.PrincipalMismatchException;
import org.tinystruct.typesafe.core.execution.PathActionExecutor;
import org.tinystruct.typesafe.core.metrics.DispatchMetrics;
import org.tinystruct.typesafe.core.testing.TestApps;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The confirmation handler's bookkeeping and the runner that executes a confirmed call. */
class ConfirmationHandlerTest {

    @BeforeAll
    static void install() {
        TestApps.install();
    }

    @BeforeEach
    void reset() {
        TestApps.Calls.last = null;
        TypesafeRuntime.install(null);
    }

    /** Throws whatever it is told to, so the handler's bookkeeping can be checked. */
    private static final class Scripted implements ConfirmationService {
        ApplicationException confirmFailure;
        ApplicationException rejectFailure;
        String seenPrincipal;

        @Override
        public String open(PendingCall call) {
            return "id";
        }

        @Override
        public DispatchResult confirm(String pendingId, String principal) throws ApplicationException {
            seenPrincipal = principal;
            if (confirmFailure != null) throw confirmFailure;
            return DispatchResult.executed("done");
        }

        @Override
        public void reject(String pendingId, String principal) throws ApplicationException {
            seenPrincipal = principal;
            if (rejectFailure != null) throw rejectFailure;
        }
    }

    private static Builder count(DispatchMetrics m) {
        return m.toJson();
    }

    // ---- ConfirmationHandler ----------------------------------------------------------------

    @Test
    void confirmCountsSuccessAndPassesTheResolvedPrincipal() throws Exception {
        Scripted service = new Scripted();
        DispatchMetrics metrics = new DispatchMetrics();
        ConfirmationHandler handler = new ConfirmationHandler(service, ctx -> "alice", metrics);

        assertEquals(DispatchResult.Status.EXECUTED, handler.confirm("id", null).getStatus());

        assertEquals("alice", service.seenPrincipal);
        assertEquals("1", count(metrics).get("confirmed").toString());
    }

    @Test
    void eachFailureKindIsCountedAndRethrown() {
        Scripted service = new Scripted();
        DispatchMetrics metrics = new DispatchMetrics();
        ConfirmationHandler handler = new ConfirmationHandler(service, ctx -> "p", metrics);

        service.confirmFailure = new ConfirmationExpiredException("id");
        assertThrows(ConfirmationExpiredException.class, () -> handler.confirm("id", null));
        service.confirmFailure = new ConfirmationConflictException("id");
        assertThrows(ConfirmationConflictException.class, () -> handler.confirm("id", null));
        service.confirmFailure = new PrincipalMismatchException("id");
        assertThrows(PrincipalMismatchException.class, () -> handler.confirm("id", null));

        Builder json = count(metrics);
        assertEquals("1", json.get("expired").toString());
        assertEquals("1", json.get("conflicts").toString());
        assertEquals("1", json.get("wrongPrincipal").toString());
        assertEquals("0", json.get("confirmed").toString());
    }

    @Test
    void rejectIsCountedAndItsFailuresToo() {
        Scripted service = new Scripted();
        DispatchMetrics metrics = new DispatchMetrics();
        ConfirmationHandler handler = new ConfirmationHandler(service, ctx -> "p", metrics);

        assertDoesNotThrow(() -> handler.reject("id", null));
        service.rejectFailure = new ConfirmationExpiredException("id");
        assertThrows(ConfirmationExpiredException.class, () -> handler.reject("id", null));
        service.rejectFailure = new PrincipalMismatchException("id");
        assertThrows(PrincipalMismatchException.class, () -> handler.reject("id", null));

        Builder json = count(metrics);
        assertEquals("1", json.get("userRejections").toString());
        assertEquals("1", json.get("expired").toString());
        assertEquals("1", json.get("wrongPrincipal").toString());
    }

    @Test
    void withoutAServiceNothingCanBeConfirmedOrRejected() {
        ConfirmationHandler handler = new ConfirmationHandler(null, ctx -> "p", new DispatchMetrics());
        assertThrows(ApplicationException.class, () -> handler.confirm("id", null));
        assertThrows(ApplicationException.class, () -> handler.reject("id", null));
    }

    // ---- ConfirmedCallRunner ----------------------------------------------------------------

    private static ConfirmedCallRunner runner(Set<String> allowed) {
        return new ConfirmedCallRunner(new ActionCatalog(allowed), new ArgumentValidator(), new PathActionExecutor());
    }

    private static Builder encoded(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return ArgumentCodec.encode(m);
    }

    @Test
    void runsAConfirmedCallWithItsOriginalTypes() throws Exception {
        Object result = runner(Set.of("create-user")).run(ActionRegistry.getInstance(), new ApplicationContext(),
                "create-user", Mode.CLI.name(), encoded("name", "John", "role", TestApps.Role.ADMIN));

        assertEquals("create-user:John:ADMIN", result);
    }

    @Test
    void refusesAnActionThatLeftTheAllowlistWhileItWasPending() {
        assertThrows(ApplicationException.class, () -> runner(Set.of("create-user")).run(
                ActionRegistry.getInstance(), new ApplicationContext(), "delete-user", Mode.CLI.name(), encoded("name", "John")));
        assertNull(TestApps.Calls.last);
    }

    @Test
    void refusesStoredArgumentsThatNoLongerValidate() {
        assertThrows(InvalidActionException.class, () -> runner(Set.of("delete-user")).run(
                ActionRegistry.getInstance(), new ApplicationContext(), "delete-user", Mode.CLI.name(), encoded("name", "a/b")));
        assertThrows(InvalidActionException.class, () -> runner(Set.of("create-user")).run(
                ActionRegistry.getInstance(), new ApplicationContext(), "create-user", Mode.CLI.name(),
                encoded("name", "John", "role", "REMOVED")));
        assertNull(TestApps.Calls.last);
    }

    @Test
    void anUnknownModeNameFallsBackToCli() throws Exception {
        assertEquals("delete-user:John", runner(Set.of("delete-user")).run(ActionRegistry.getInstance(),
                new ApplicationContext(), "delete-user", "NOT_A_MODE", encoded("name", "John")));
        assertEquals("delete-user:John", runner(Set.of("delete-user")).run(ActionRegistry.getInstance(),
                new ApplicationContext(), "delete-user", null, encoded("name", "John")));
    }
}
