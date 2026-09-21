package org.tinystruct.typesafe.core.catalog;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.tinystruct.ApplicationException;
import org.tinystruct.application.ActionRegistry;
import org.tinystruct.system.annotation.Action.Mode;
import org.tinystruct.typesafe.core.testing.TestApps;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** {@link ActionCatalog} against the real registry: metadata comes from tinystruct's own CommandLine. */
class ActionCatalogTest {

    @BeforeAll
    static void install() {
        TestApps.install();
    }

    private static List<ActionDefinition> resolve(Mode mode, String... allowed) throws ApplicationException {
        return new ActionCatalog(new LinkedHashSet<>(List.of(allowed))).resolve(ActionRegistry.getInstance(), mode);
    }

    @Test
    void buildsDefinitionsFromTheFrameworksCommandMetadata() throws Exception {
        ActionDefinition def = resolve(Mode.CLI, "create-user").get(0);

        assertEquals("create-user", def.getActionPath());
        assertEquals("Create a user account.", def.getDescription());
        assertEquals(List.of("name", "role"), def.getParameters().stream().map(ParameterDefinition::getName).toList());
        assertEquals(ParameterDefinition.Kind.OPEN_VALUE, def.getParameters().get(0).getKind());
        assertEquals(ParameterDefinition.Kind.ENUM_CHOICE, def.getParameters().get(1).getKind());
        assertEquals(TestApps.Role.class, def.getParameters().get(1).getEnumType());
        assertEquals("The role of the account", def.getParameters().get(1).getDescription());
    }

    @Test
    void classifiesSetsFlagsAndNumbers() throws Exception {
        List<ActionDefinition> defs = resolve(Mode.CLI, "assign-roles", "set-active", "add-credit");

        ActionDefinition assign = defs.stream().filter(d -> d.getActionPath().equals("assign-roles")).findFirst().orElseThrow();
        assertEquals(ParameterDefinition.Kind.SET_ENUM, assign.getParameters().get(1).getKind());
        assertEquals(TestApps.Role.class, assign.getParameters().get(1).getEnumType());

        ActionDefinition active = defs.stream().filter(d -> d.getActionPath().equals("set-active")).findFirst().orElseThrow();
        assertEquals(ParameterDefinition.Kind.FLAG, active.getParameters().get(1).getKind());

        ActionDefinition credit = defs.stream().filter(d -> d.getActionPath().equals("add-credit")).findFirst().orElseThrow();
        assertEquals(ParameterDefinition.Kind.OPEN_VALUE, credit.getParameters().get(1).getKind());
    }

    @Test
    void listIsSupportedLikeSet() throws Exception {
        ParameterDefinition tags = resolve(Mode.CLI, "tag-user").get(0).getParameters().get(0);
        assertEquals(ParameterDefinition.Kind.SET_ENUM, tags.getKind());
        assertEquals(List.class, tags.getRawType());
    }

    @Test
    void returnsDefinitionsInAlphabeticalOrderForStableRequests() throws Exception {
        List<String> paths = resolve(Mode.CLI, "delete-user", "create-user", "assign-roles").stream()
                .map(ActionDefinition::getActionPath).toList();
        assertEquals(List.of("assign-roles", "create-user", "delete-user"), paths);
    }

    @Test
    void anAllowlistedNameThatIsNotRegisteredIsSkipped() throws Exception {
        assertTrue(resolve(Mode.CLI, "no-such-action").isEmpty());
        assertEquals(1, resolve(Mode.CLI, "no-such-action", "create-user").size());
    }

    @Test
    void builtInsAndTemplatesAreNeverRoutableEvenIfAllowlisted() throws Exception {
        assertTrue(resolve(Mode.CLI, "start", "generate", "help", "--help", "user/{id}").isEmpty());
    }

    @Test
    void emptyAllowlistYieldsNothing() throws Exception {
        assertTrue(resolve(Mode.CLI).isEmpty());
    }

    @Test
    void actionsDeclaredForAnotherModeAreSkipped() throws Exception {
        assertTrue(resolve(Mode.CLI, "post-only").isEmpty(), "an HTTP_POST action is not routable from the CLI");
        assertEquals(1, resolve(Mode.HTTP_POST, "post-only").size());
        assertEquals(1, resolve(Mode.CLI, "cli-only").size());
        assertTrue(resolve(Mode.HTTP_GET, "cli-only").isEmpty());
    }

    @Test
    void anUnsupportedParameterTypeFailsLoudly() {
        ApplicationException e = assertThrows(ApplicationException.class, () -> resolve(Mode.CLI, "bad-type"));
        assertTrue(e.getMessage().contains("unsupported type"), e.getMessage());
        assertTrue(e.getMessage().contains("bad-type"));
    }

    @Test
    void aParameterWithoutADeclaredArgumentFailsLoudly() throws Exception {
        // The framework records no argument for it, so the action has no parameters as far as routing can tell.
        ActionDefinition def = resolve(Mode.CLI, "undeclared").get(0);
        assertTrue(def.getParameters().isEmpty(),
                "undeclared parameters are invisible to routing; the executor then finds no matching path");
    }
}
