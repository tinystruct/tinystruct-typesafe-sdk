package org.tinystruct.typesafe.workflow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tinystruct.AbstractApplication;
import org.tinystruct.ApplicationContext;
import org.tinystruct.ApplicationException;
import org.tinystruct.system.ApplicationManager;
import org.tinystruct.system.Settings;
import org.tinystruct.system.annotation.Action;
import org.tinystruct.system.annotation.Argument;
import org.tinystruct.typesafe.core.api.DispatchResult;
import org.tinystruct.typesafe.core.TypesafeRuntime;
import org.tinystruct.typesafe.core.argument.ArgumentValidator;
import org.tinystruct.typesafe.core.catalog.ActionCatalog;
import org.tinystruct.typesafe.core.confirmation.ConfirmationConflictException;
import org.tinystruct.typesafe.core.confirmation.ConfirmationExpiredException;
import org.tinystruct.typesafe.core.confirmation.ConfirmationHandler;
import org.tinystruct.typesafe.core.confirmation.ConfirmedCallRunner;
import org.tinystruct.typesafe.core.confirmation.PendingCall;
import org.tinystruct.typesafe.core.confirmation.PrincipalMismatchException;
import org.tinystruct.typesafe.core.execution.ActionExecutor;
import org.tinystruct.typesafe.core.execution.PathActionExecutor;
import org.tinystruct.typesafe.core.metrics.DispatchMetrics;
import org.tinystruct.workflow.ExecutionContext;
import org.tinystruct.workflow.WorkflowEngine;
import org.tinystruct.workflow.WorkflowStatus;
import org.tinystruct.workflow.repository.FileSnapshotRepository;
import org.tinystruct.workflow.repository.MemorySnapshotRepository;
import org.tinystruct.workflow.repository.SnapshotRepository;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The confirmation flow end to end: a real {@link WorkflowEngine}, the real {@code ActionRegistry}
 * and real actions. Nothing about the workflow or tinystruct is mocked.
 */
class WorkflowConfirmationServiceTest {

    private static final String ALICE = "user:alice";
    private static final AtomicInteger RUNS = new AtomicInteger();

    public enum Role { ADMIN, VIEWER }

    public static class TargetApp extends AbstractApplication {
        @Override
        public void init() {
            setTemplateRequired(false);
        }

        @Override
        public String version() {
            return "1.0";
        }

        @Action(value = "wipe-user", description = "Delete a user.",
                arguments = {@Argument(key = "name", description = "The user")})
        public String wipeUser(String name) {
            RUNS.incrementAndGet();
            return "wiped " + name;
        }

        @Action(value = "grant-roles", description = "Grant roles.",
                arguments = {
                        @Argument(key = "name", description = "The user"),
                        @Argument(key = "roles", description = "The roles")})
        public String grantRoles(String name, Set<Role> roles) {
            RUNS.incrementAndGet();
            return "granted " + name + " " + roles;
        }

        @Action(value = "explode", description = "Always fails.",
                arguments = {@Argument(key = "name", description = "The user")})
        public String explode(String name) throws ApplicationException {
            RUNS.incrementAndGet();
            throw new ApplicationException("kaboom");
        }
    }

    private SnapshotRepository repository;
    private WorkflowEngine engine;
    private WorkflowConfirmationService service;

    @BeforeAll
    static void install() {
        ApplicationManager.install(new TargetApp(), new Settings());
        ApplicationManager.install(new SemanticConfirmApplication(), new Settings());
    }

    @BeforeEach
    void setUp() {
        RUNS.set(0);
        repository = new MemorySnapshotRepository();
        engine = new WorkflowEngine(repository);
        EngineHolder.reset(engine, repository);
        service = new WorkflowConfirmationService(engine, repository, false);
        allow("wipe-user", "grant-roles", "explode");
    }

    @AfterEach
    void tearDown() {
        TypesafeRuntime.install(null);
        EngineHolder.reset(null, null);
    }

    /** Installs the runtime that node {@code execute} will use, with the given allowlist. */
    private void allow(String... actions) {
        Set<String> allowed = Set.of(actions);
        ArgumentValidator validator = new ArgumentValidator();
        ActionExecutor executor = new PathActionExecutor();
        DispatchMetrics metrics = new DispatchMetrics();
        TypesafeRuntime.install(new TypesafeRuntime(
                (input, registry, context) -> DispatchResult.rejected("not used"),
                new ConfirmationHandler(service, ctx -> ALICE, metrics),
                new ConfirmedCallRunner(new ActionCatalog(allowed), validator, executor),
                metrics));
    }

    private static PendingCall call(String action, Map<String, Object> args, String principal, long expiresInMs) {
        long now = System.currentTimeMillis();
        return new PendingCall(action, args, principal, 0.93, now, now + expiresInMs, "CLI");
    }

    private static Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private String open(String action, Map<String, Object> args) throws Exception {
        return service.open(call(action, args, ALICE, 300_000));
    }

    // ---- opening ----------------------------------------------------------------------------

    @Test
    void openingHoldsTheCallAsAWaitingExecutionWithoutRunningIt() throws Exception {
        String id = open("wipe-user", args("name", "John"));

        ExecutionContext held = engine.getExecution(id);
        assertEquals(WorkflowStatus.WAITING, held.getStatus());
        assertEquals(EngineHolder.WORKFLOW_ID, held.getWorkflowId());
        assertEquals(ALICE, held.getVariables().get("__principal__").toString());
        assertEquals(0, RUNS.get(), "nothing runs before confirmation");
    }

    // ---- confirming -------------------------------------------------------------------------

    @Test
    void confirmRunsTheActionOnceAndReturnsItsResult() throws Exception {
        String id = open("wipe-user", args("name", "John"));

        DispatchResult result = service.confirm(id, ALICE);

        assertEquals(DispatchResult.Status.EXECUTED, result.getStatus());
        assertEquals("wiped John", result.getResult());
        assertEquals(1, RUNS.get());
    }

    @Test
    void theSnapshotIsDeletedOnceTheCallIsFinished() throws Exception {
        String id = open("wipe-user", args("name", "John"));
        assertNotNull(repository.load(id));

        service.confirm(id, ALICE);

        assertNull(repository.load(id), "the arguments must not stay on disk after the call is done");
    }

    @Test
    void theSnapshotCanBeKeptForAudit() throws Exception {
        service = new WorkflowConfirmationService(engine, repository, true);
        String id = open("wipe-user", args("name", "John"));

        service.confirm(id, ALICE);

        assertEquals(WorkflowStatus.COMPLETED, repository.load(id).getStatus());
    }

    @Test
    void typedArgumentsSurviveTheSnapshotIncludingSets() throws Exception {
        String id = open("grant-roles", args("name", "John", "roles", new java.util.LinkedHashSet<>(List.of(Role.ADMIN, Role.VIEWER))));

        assertEquals("granted John [ADMIN, VIEWER]", service.confirm(id, ALICE).getResult());
    }

    @Test
    void aSecondConfirmDoesNotRunTheActionAgain() throws Exception {
        service = new WorkflowConfirmationService(engine, repository, true);
        String id = open("wipe-user", args("name", "John"));
        service.confirm(id, ALICE);

        assertThrows(ConfirmationConflictException.class, () -> service.confirm(id, ALICE));
        assertEquals(1, RUNS.get());
    }

    @Test
    void afterDeletionAConfirmSimplyFindsNothing() throws Exception {
        String id = open("wipe-user", args("name", "John"));
        service.confirm(id, ALICE);

        assertThrows(ApplicationException.class, () -> service.confirm(id, ALICE));
        assertEquals(1, RUNS.get());
    }

    @Test
    void twoConcurrentConfirmsRunTheActionExactlyOnce() throws Exception {
        service = new WorkflowConfirmationService(engine, repository, true);
        String id = open("wipe-user", args("name", "John"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                try {
                    return service.confirm(id, ALICE);
                } catch (ApplicationException e) {
                    return e;
                }
            }));
        }
        go.countDown();
        int successes = 0;
        int conflicts = 0;
        for (Future<Object> f : futures) {
            Object outcome = f.get(30, TimeUnit.SECONDS);
            if (outcome instanceof DispatchResult) successes++;
            if (outcome instanceof ConfirmationConflictException) conflicts++;
        }
        pool.shutdownNow();

        assertEquals(1, successes, "exactly one confirm wins");
        assertEquals(1, conflicts, "the other is told it lost");
        assertEquals(1, RUNS.get());
    }

    // ---- authorization ----------------------------------------------------------------------

    @Test
    void someoneElseCannotConfirm() throws Exception {
        String id = open("wipe-user", args("name", "John"));

        assertThrows(PrincipalMismatchException.class, () -> service.confirm(id, "user:mallory"));

        assertEquals(WorkflowStatus.WAITING, engine.getExecution(id).getStatus(), "the real owner can still confirm");
        assertEquals(0, RUNS.get());
        assertEquals("wiped John", service.confirm(id, ALICE).getResult());
    }

    @Test
    void someoneElseCannotRejectEither() throws Exception {
        String id = open("wipe-user", args("name", "John"));

        assertThrows(PrincipalMismatchException.class, () -> service.reject(id, "user:mallory"));

        assertEquals(WorkflowStatus.WAITING, engine.getExecution(id).getStatus());
    }

    // ---- rejecting and expiry ---------------------------------------------------------------

    @Test
    void rejectingCancelsWithoutRunningAndCleansUp() throws Exception {
        service = new WorkflowConfirmationService(engine, repository, true);
        String id = open("wipe-user", args("name", "John"));

        service.reject(id, ALICE);

        assertEquals(WorkflowStatus.CANCELLED, repository.load(id).getStatus(), "a rejection is a cancel, not a failure");
        assertEquals(0, RUNS.get());
    }

    @Test
    void rejectingDeletesTheSnapshotByDefault() throws Exception {
        String id = open("wipe-user", args("name", "John"));
        service.reject(id, ALICE);
        assertNull(repository.load(id));
    }

    @Test
    void anExpiredCallCannotBeConfirmed() throws Exception {
        String id = service.open(call("wipe-user", args("name", "John"), ALICE, -1_000));

        assertThrows(ConfirmationExpiredException.class, () -> service.confirm(id, ALICE));

        assertEquals(0, RUNS.get());
        assertNull(repository.load(id), "an expired call is cancelled and cleaned up");
    }

    @Test
    void anExpiredCallCannotBeRejectedEitherButIsCleanedUp() throws Exception {
        String id = service.open(call("wipe-user", args("name", "John"), ALICE, -1_000));

        assertThrows(ConfirmationExpiredException.class, () -> service.reject(id, ALICE));
        assertNull(repository.load(id));
    }

    // ---- restarts ---------------------------------------------------------------------------

    @Test
    void aCallOpenedBeforeARestartCanStillBeConfirmed() throws Exception {
        String id = open("wipe-user", args("name", "John"));

        // "Restart": a brand-new engine over the same storage. Its event registry is empty.
        WorkflowEngine restarted = new WorkflowEngine(repository);
        EngineHolder.reset(restarted, repository);
        WorkflowConfirmationService afterRestart = new WorkflowConfirmationService(restarted, repository, false);

        assertEquals("wiped John", afterRestart.confirm(id, ALICE).getResult());
        assertEquals(1, RUNS.get());
    }

    // ---- what can go wrong while confirmed --------------------------------------------------

    @Test
    void ifTheConfirmedActionFailsTheCallerSeesThatNotAConflict() throws Exception {
        String id = open("explode", args("name", "John"));

        ApplicationException e = assertThrows(ApplicationException.class, () -> service.confirm(id, ALICE));

        assertFalse(e instanceof ConfirmationConflictException, "a failing action is not a lost race");
        assertTrue(e.getMessage().contains("kaboom") || e.getMessage().contains("failed"), e.getMessage());
        assertEquals(1, RUNS.get());
        assertNull(repository.load(id), "a failed call is cleaned up too");
    }

    @Test
    void anActionRemovedFromTheAllowlistWhilePendingIsNotRun() throws Exception {
        String id = open("wipe-user", args("name", "John"));

        allow("grant-roles"); // wipe-user is no longer allowlisted

        ApplicationException e = assertThrows(ApplicationException.class, () -> service.confirm(id, ALICE));
        assertFalse(e instanceof ConfirmationConflictException);
        assertEquals(0, RUNS.get());
    }

    @Test
    void unknownIdsAreNotFound() {
        assertThrows(ApplicationException.class, () -> service.confirm("no-such-id", ALICE));
        assertThrows(ApplicationException.class, () -> service.reject("no-such-id", ALICE));
    }

    @Test
    void anExecutionOfSomeOtherWorkflowIsNotACall() throws Exception {
        engine.registerWorkflow(new org.tinystruct.workflow.WorkflowDefinition("other").addNode("semantic-confirm/await"));
        // Not startable without the pending-call variable, so store a foreign execution directly.
        ExecutionContext foreign = new ExecutionContext();
        foreign.setExecutionId("foreign-1");
        foreign.setWorkflowId("other");
        foreign.setStatus(WorkflowStatus.WAITING);
        repository.save(foreign);

        assertThrows(ApplicationException.class, () -> service.confirm("foreign-1", ALICE));
    }

    // ---- the nodes are not a back door ------------------------------------------------------

    @Test
    void nodesRefuseToRunOutsideAWorkflow() {
        assertThrows(ApplicationException.class,
                () -> ApplicationManager.call("semantic-confirm/execute", new ApplicationContext()));
        assertThrows(ApplicationException.class,
                () -> ApplicationManager.call("semantic-confirm/await", new ApplicationContext()));
        assertEquals(0, RUNS.get());
    }

    @Test
    void anUnconfiguredServiceSaysSo() {
        WorkflowConfirmationService bare = new WorkflowConfirmationService();
        assertThrows(ApplicationException.class, () -> bare.confirm("x", ALICE));
        assertThrows(ApplicationException.class, () -> bare.open(call("wipe-user", args(), ALICE, 1000)));
    }

    // ---- storage ----------------------------------------------------------------------------

    @Test
    void storedCallsParseBackWithTheirExpiryAndMode() throws Exception {
        PendingCall original = call("wipe-user", args("name", "John"), ALICE, 300_000);

        PendingCallSerializer.StoredCall stored = PendingCallSerializer.fromBuilder(PendingCallSerializer.toBuilder(original));

        assertEquals("wipe-user", stored.actionPath());
        assertEquals("CLI", stored.mode());
        assertEquals(ALICE, stored.principal());
        assertEquals(original.getExpiresAt(), stored.expiresAt());
        assertEquals("John", stored.encodedArguments().get("name").toString());
    }

    @Test
    void aMalformedStoredCallIsRefused() {
        org.tinystruct.data.component.Builder broken = new org.tinystruct.data.component.Builder();
        broken.put("actionPath", "wipe-user");
        assertThrows(ApplicationException.class, () -> PendingCallSerializer.fromBuilder(broken));
    }

    @Test
    void engineHolderBuildsTheRepositoryTheConfigurationNamesAndRejectsUnknownOnes(@TempDir Path dir) {
        EngineHolder.reset(null, null);
        EngineHolder.getOrCreate(TestConfig.of(EngineHolder.REPOSITORY, "file", EngineHolder.SNAPSHOT_DIR, dir.toString()));
        assertInstanceOf(FileSnapshotRepository.class, EngineHolder.repository());

        EngineHolder.reset(null, null);
        assertThrows(IllegalArgumentException.class,
                () -> EngineHolder.getOrCreate(TestConfig.of(EngineHolder.REPOSITORY, "carrier-pigeon")));

        EngineHolder.reset(null, null);
        EngineHolder.getOrCreate(TestConfig.of());
        assertInstanceOf(MemorySnapshotRepository.class, EngineHolder.repository(), "memory is the default");
    }

    @Test
    void keepCompletedReadsItsSetting() {
        assertTrue(EngineHolder.keepCompleted(TestConfig.of(EngineHolder.KEEP_COMPLETED, "true")));
        assertFalse(EngineHolder.keepCompleted(TestConfig.of()));
    }

    @Test
    void configureBuildsTheEngineFromConfiguration() {
        EngineHolder.reset(null, null);
        WorkflowConfirmationService configured = new WorkflowConfirmationService();
        configured.configure(TestConfig.of(EngineHolder.KEEP_COMPLETED, "true"));

        assertNotNull(EngineHolder.repository());
        assertDoesNotThrow(() -> configured.open(call("wipe-user", args("name", "John"), ALICE, 60_000)));
    }

    @Test
    void confirmationEventCarriesItsPayload() {
        assertTrue(new ConfirmationEvent("e", true).isConfirmed());
        assertFalse(new ConfirmationEvent("e", false).isConfirmed());
        assertEquals("e", new ConfirmationEvent("e", true).getExecutionId());
    }

    // ---- defensive branches of the nodes ----------------------------------------------------

    /** Starts the workflow with hand-built variables, as a corrupted or tampered snapshot would hold. */
    private String startWith(Object pendingCall, long expiresAt) throws Exception {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put(SemanticConfirmApplication.VAR_PENDING_CALL, pendingCall);
        variables.put(SemanticConfirmApplication.VAR_PRINCIPAL, ALICE);
        variables.put(SemanticConfirmApplication.VAR_EXPIRES_AT, expiresAt);
        return engine.start(EngineHolder.WORKFLOW_ID, variables);
    }

    @Test
    void aCallThatExpiredBetweenTheCheckAndTheNodeDoesNotRun() throws Exception {
        long future = System.currentTimeMillis() + 60_000;
        PendingCall stale = call("wipe-user", args("name", "John"), ALICE, -1_000);
        String id = startWith(PendingCallSerializer.toBuilder(stale), future);

        ApplicationException e = assertThrows(ApplicationException.class, () -> service.confirm(id, ALICE));

        assertFalse(e instanceof ConfirmationConflictException);
        assertEquals(0, RUNS.get());
    }

    @Test
    void aMalformedStoredCallDoesNotRun() throws Exception {
        String id = startWith("not-a-call", System.currentTimeMillis() + 60_000);

        assertThrows(ApplicationException.class, () -> service.confirm(id, ALICE));
        assertEquals(0, RUNS.get());
    }

    @Test
    void theExecuteNodeRefusesAResumeThatIsNotAPositiveConfirmation() throws Exception {
        String id = open("wipe-user", args("name", "John"));

        // Resuming with a "no" through the engine must never run the action.
        assertThrows(ApplicationException.class, () -> engine.resume(id, new ConfirmationEvent(id, false)));

        assertEquals(WorkflowStatus.FAILED, repository.load(id).getStatus());
        assertEquals(0, RUNS.get());
    }

    @Test
    void aStorageFailureWhileCleaningUpDoesNotFailTheConfirmedCall() throws Exception {
        SnapshotRepository failingDelete = new SnapshotRepository() {
            @Override
            public void save(ExecutionContext context) throws org.tinystruct.workflow.SnapshotIOException {
                repository.save(context);
            }

            @Override
            public ExecutionContext load(String executionId) throws org.tinystruct.workflow.SnapshotIOException {
                return repository.load(executionId);
            }

            @Override
            public void delete(String executionId) throws org.tinystruct.workflow.SnapshotIOException {
                throw new org.tinystruct.workflow.SnapshotIOException("disk full");
            }
        };
        WorkflowEngine failing = new WorkflowEngine(failingDelete);
        EngineHolder.reset(failing, failingDelete);
        WorkflowConfirmationService flaky = new WorkflowConfirmationService(failing, failingDelete, false);
        String id = flaky.open(call("wipe-user", args("name", "John"), ALICE, 60_000));

        assertEquals("wiped John", flaky.confirm(id, ALICE).getResult(), "the action ran; only the cleanup failed");
    }

    // ---- storage parsing --------------------------------------------------------------------

    @Test
    void storedArgumentsMayComeBackAsAJsonString() throws Exception {
        org.tinystruct.data.component.Builder stored = PendingCallSerializer.toBuilder(
                call("wipe-user", args("name", "John"), ALICE, 60_000));
        stored.put("arguments", "{\"name\":\"John\"}");

        assertEquals("John", PendingCallSerializer.fromBuilder(stored).encodedArguments().get("name").toString());
    }

    @Test
    void aStoredCallWithoutAModeDefaultsAndOneWithABadTimeIsRefused() throws Exception {
        org.tinystruct.data.component.Builder stored = PendingCallSerializer.toBuilder(
                call("wipe-user", args("name", "John"), ALICE, 60_000));
        stored.remove("mode");
        assertEquals("DEFAULT", PendingCallSerializer.fromBuilder(stored).mode());

        stored.put("expiresAt", "tomorrow");
        assertThrows(ApplicationException.class, () -> PendingCallSerializer.fromBuilder(stored));
    }

    @Test
    void openingFailsCleanlyWhenTheWorkflowIsNotRegistered() {
        WorkflowEngine bare = new WorkflowEngine(new MemorySnapshotRepository()); // no definition registered
        WorkflowConfirmationService unregistered = new WorkflowConfirmationService(bare, repository, false);

        assertThrows(ApplicationException.class,
                () -> unregistered.open(call("wipe-user", args("name", "John"), ALICE, 60_000)));
    }
}
