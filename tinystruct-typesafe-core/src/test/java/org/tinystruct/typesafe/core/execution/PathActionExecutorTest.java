package org.tinystruct.typesafe.core.execution;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tinystruct.ApplicationContext;
import org.tinystruct.ApplicationException;
import org.tinystruct.application.ActionRegistry;
import org.tinystruct.system.annotation.Action.Mode;
import org.tinystruct.typesafe.core.argument.InvalidActionException;
import org.tinystruct.typesafe.core.catalog.ActionDefinition;
import org.tinystruct.typesafe.core.catalog.ParameterDefinition;
import org.tinystruct.typesafe.core.testing.TestApps;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** {@link PathActionExecutor} dispatching through tinystruct's own {@code ApplicationManager.call}. */
class PathActionExecutorTest {

    private final PathActionExecutor executor = new PathActionExecutor();

    @BeforeAll
    static void install() {
        TestApps.install();
    }

    @BeforeEach
    void reset() {
        TestApps.Calls.last = null;
    }

    private static ActionDefinition def(String path, Mode mode, ParameterDefinition... params) {
        return new ActionDefinition(path, path, List.of(params), mode);
    }

    private static ParameterDefinition text(String name, boolean optional) {
        return new ParameterDefinition(name, "", ParameterDefinition.Kind.OPEN_VALUE, optional, String.class, String.class, null);
    }

    private static ParameterDefinition role(String name) {
        return new ParameterDefinition(name, "", ParameterDefinition.Kind.ENUM_CHOICE, false,
                TestApps.Role.class, TestApps.Role.class, TestApps.Role.class);
    }

    private static Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    // ---- path building ----------------------------------------------------------------------

    @Test
    void buildsTheSamePathAUserWouldType() throws Exception {
        assertEquals("create-user/John/ADMIN",
                PathActionExecutor.pathFor(def("create-user", Mode.DEFAULT, text("name", false), role("role")),
                        args("name", "John", "role", TestApps.Role.ADMIN)));
    }

    @Test
    void aSetBecomesACommaSeparatedSegment() throws Exception {
        ParameterDefinition roles = new ParameterDefinition("roles", "", ParameterDefinition.Kind.SET_ENUM, false,
                Set.class, Set.class, TestApps.Role.class);
        assertEquals("assign-roles/Al/ADMIN,VIEWER",
                PathActionExecutor.pathFor(def("assign-roles", Mode.DEFAULT, text("name", false), roles),
                        args("name", "Al", "roles", new LinkedHashSet<>(List.of(TestApps.Role.ADMIN, TestApps.Role.VIEWER)))));
    }

    @Test
    void anOptionalParameterCanBeOmittedAtTheEnd() throws Exception {
        assertEquals("create-customer/Al",
                PathActionExecutor.pathFor(def("create-customer", Mode.DEFAULT, text("name", false), text("email", true)),
                        args("name", "Al", "email", null)));
    }

    @Test
    void anOmittedParameterFollowedByASuppliedOneIsRefused() {
        assertThrows(InvalidActionException.class, () ->
                PathActionExecutor.pathFor(def("x", Mode.DEFAULT, text("a", true), text("b", false)),
                        args("a", null, "b", "value")));
    }

    @Test
    void anEmptySetCountsAsOmitted() throws Exception {
        ParameterDefinition roles = new ParameterDefinition("roles", "", ParameterDefinition.Kind.SET_ENUM, true,
                Set.class, Set.class, TestApps.Role.class);
        assertEquals("assign-roles/Al",
                PathActionExecutor.pathFor(def("assign-roles", Mode.DEFAULT, text("name", false), roles),
                        args("name", "Al", "roles", Set.of())));
    }

    // ---- execution through the framework ----------------------------------------------------

    @Test
    void runsAnActionWithEnumAndSpacesInTheValue() throws Exception {
        Object result = executor.execute(
                def("create-user", Mode.DEFAULT, text("name", false), role("role")),
                args("name", "John Smith", "role", TestApps.Role.ADMIN), new ApplicationContext(), Mode.CLI);

        assertEquals("create-user:John Smith:ADMIN", result);
    }

    @Test
    void frameworkConvertsCollectionsAndPrimitives() throws Exception {
        ParameterDefinition roles = new ParameterDefinition("roles", "", ParameterDefinition.Kind.SET_ENUM, false,
                Set.class, Set.class, TestApps.Role.class);
        assertEquals("assign-roles:Al:[ADMIN, EDITOR]", executor.execute(
                def("assign-roles", Mode.DEFAULT, text("name", false), roles),
                args("name", "Al", "roles", new LinkedHashSet<>(List.of(TestApps.Role.ADMIN, TestApps.Role.EDITOR))),
                new ApplicationContext(), Mode.CLI));

        ParameterDefinition amount = new ParameterDefinition("amount", "", ParameterDefinition.Kind.OPEN_VALUE, false,
                int.class, int.class, null);
        assertEquals("add-credit:Al:25", executor.execute(
                def("add-credit", Mode.DEFAULT, text("name", false), amount),
                args("name", "Al", "amount", "25"), new ApplicationContext(), Mode.CLI));

        ParameterDefinition flag = new ParameterDefinition("active", "", ParameterDefinition.Kind.FLAG, false,
                boolean.class, boolean.class, null);
        assertEquals("set-active:Al:true", executor.execute(
                def("set-active", Mode.DEFAULT, text("name", false), flag),
                args("name", "Al", "active", true), new ApplicationContext(), Mode.CLI));
    }

    @Test
    void trailingOptionalArgumentUsesTheShorterOverload() throws Exception {
        Object result = executor.execute(
                def("create-customer", Mode.DEFAULT, text("name", false), text("email", true)),
                args("name", "Al", "email", null), new ApplicationContext(), Mode.CLI);
        assertEquals("create-customer:Al", result);

        Object both = executor.execute(
                def("create-customer", Mode.DEFAULT, text("name", false), text("email", true)),
                args("name", "Al", "email", "al@example.com"), new ApplicationContext(), Mode.CLI);
        assertEquals("create-customer:Al:al@example.com", both);
    }

    @Test
    void theFrameworkEnforcesTheActionsMode() {
        ApplicationException e = assertThrows(ApplicationException.class, () -> executor.execute(
                def("post-only", Mode.HTTP_POST, text("name", false)),
                args("name", "x"), new ApplicationContext(), Mode.CLI));
        assertNull(TestApps.Calls.last, "the action must not have run");
        assertNotNull(e.getMessage());
    }

    @Test
    void aCallThatMatchesNoRegisteredActionIsNotFound() {
        // A definition that claims more parameters than the action really has never resolves.
        assertThrows(ApplicationException.class, () -> executor.execute(
                def("delete-user", Mode.DEFAULT, text("name", false), text("extra", false)),
                args("name", "x", "extra", "y"), new ApplicationContext(), Mode.CLI));
        assertNull(TestApps.Calls.last);
    }

    @Test
    void neverRunsAnotherActionThatHappensToMatchThePath() {
        // "create-user/admin/ADMIN" matches both create-user(name, role) and the shadow action.
        // Whichever the registry picks, only the allowlisted create-user may run.
        ActionDefinition createUser = new ActionDefinition("create-user", "d",
                List.of(text("name", false), role("role")), Mode.DEFAULT,
                new TestApps.UserApp().getName());
        try {
            executor.execute(createUser, args("name", "admin", "role", TestApps.Role.ADMIN),
                    new ApplicationContext(), Mode.CLI);
        } catch (ApplicationException refused) {
            // acceptable: the shadow was resolved and refused
        }
        assertTrue(TestApps.Calls.last == null || TestApps.Calls.last.startsWith("create-user:"),
                "ran: " + TestApps.Calls.last);
    }

    @Test
    void refusesAnApplicationThatIsNotTheExpectedOne() {
        ActionDefinition wrongApp = new ActionDefinition("delete-user", "d", List.of(text("name", false)),
                Mode.DEFAULT, "some.other.Application");
        assertThrows(ApplicationException.class, () -> executor.execute(wrongApp, args("name", "x"),
                new ApplicationContext(), Mode.CLI));
        assertNull(TestApps.Calls.last);
    }
}
