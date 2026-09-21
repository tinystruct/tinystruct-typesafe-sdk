package org.tinystruct.typesafe.demo;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.tinystruct.application.ActionRegistry;
import org.tinystruct.data.component.Builder;
import org.tinystruct.system.ApplicationManager;
import org.tinystruct.system.Settings;
import org.tinystruct.system.annotation.Action.Mode;
import org.tinystruct.typesafe.client.testing.MockTypesafeClient;
import org.tinystruct.typesafe.core.api.DispatchResult;
import org.tinystruct.typesafe.core.argument.ArgumentValidator;
import org.tinystruct.typesafe.core.candidate.TokenSpanExtractor;
import org.tinystruct.typesafe.core.catalog.ActionCatalog;
import org.tinystruct.typesafe.core.catalog.ActionDefinition;
import org.tinystruct.typesafe.core.catalog.ParameterDefinition;
import org.tinystruct.typesafe.core.config.RoutingSettings;
import org.tinystruct.typesafe.core.config.TypesafeConfig;
import org.tinystruct.typesafe.core.confirmation.DefaultPrincipalResolver;
import org.tinystruct.typesafe.core.execution.PathActionExecutor;
import org.tinystruct.typesafe.core.metrics.DispatchMetrics;
import org.tinystruct.typesafe.core.pipeline.DispatchPipeline;
import org.tinystruct.typesafe.core.policy.AmbiguousIntentException;
import org.tinystruct.typesafe.core.policy.ConfidencePolicy;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The shipped demo, checked against the shipped {@code application.properties}: every allowlisted
 * action must resolve into questions, and the worked examples must run end to end.
 */
class DemoApplicationsTest {

    private static Settings config;
    private static Set<String> allowed;

    @BeforeAll
    static void install() {
        config = new Settings(); // reads the demo's application.properties from the classpath
        ApplicationManager.install(new UserManagementApplication(), config);
        ApplicationManager.install(new CrmApplication(), config);
        ApplicationManager.install(new HelpDeskApplication(), config);
        allowed = TypesafeConfig.csv(config.get(TypesafeConfig.ALLOWED_ACTIONS));
    }

    private static DispatchPipeline pipeline(MockTypesafeClient client) {
        RoutingSettings settings = RoutingSettings.from(config);
        return DispatchPipeline.of(client, new TokenSpanExtractor(), ConfidencePolicy.from(config),
                new ArgumentValidator(settings.maxArgumentLength()), new PathActionExecutor(), null,
                new DefaultPrincipalResolver(), settings, new DispatchMetrics());
    }

    private static Builder json(Object result) throws Exception {
        Builder b = new Builder();
        b.parse(result.toString());
        return b;
    }

    @Test
    void theShippedConfigurationAllowlistsTenActions() {
        assertEquals(10, allowed.size());
        assertTrue(allowed.containsAll(TypesafeConfig.csv(config.get(TypesafeConfig.CONFIRM_ACTIONS))),
                "every confirm-action must itself be allowlisted");
    }

    @Test
    void everyAllowlistedActionResolvesIntoDefinitions() throws Exception {
        List<ActionDefinition> defs = new ActionCatalog(allowed).resolve(ActionRegistry.getInstance(), Mode.CLI);

        assertEquals(allowed.size(), defs.size(), "an allowlisted action that cannot be resolved is a configuration error");
        for (ActionDefinition def : defs) {
            assertFalse(def.getDescription().isBlank(), def.getActionPath());
            def.getParameters().forEach(p -> assertFalse(p.getDescription().isBlank(),
                    def.getActionPath() + "." + p.getName() + " needs a description for the model"));
        }
    }

    @Test
    void theRolesActionIsAskedAsASet() throws Exception {
        ActionDefinition assign = new ActionCatalog(Set.of("assign-roles")).resolve(ActionRegistry.getInstance(), Mode.CLI).get(0);
        assertEquals(ParameterDefinition.Kind.SET_ENUM, assign.getParameters().get(1).getKind());
    }

    @Test
    void createAnAdminAccountForJohn() throws Exception {
        MockTypesafeClient client = new MockTypesafeClient();
        client.addChoiceAnswer("__tool__", "create-user", 0.95);
        client.addChoiceAnswer("create-user.name", "John", 0.97);
        client.addChoiceAnswer("create-user.role", "ADMIN", 0.96);

        DispatchResult result = pipeline(client).route("create an admin account for John", ActionRegistry.getInstance());

        assertEquals(DispatchResult.Status.EXECUTED, result.getStatus());
        Builder out = json(result.getResult());
        assertEquals("John", out.get("name").toString());
        assertEquals("ADMIN", out.get("role").toString());
    }

    @Test
    void giveAliceEditorAndViewerAccess() throws Exception {
        MockTypesafeClient client = new MockTypesafeClient();
        client.addChoiceAnswer("__tool__", "assign-roles", 0.95);
        client.addChoiceAnswer("assign-roles.name", "Alice", 0.97);
        client.addNoulAnswer("assign-roles.roles.ADMIN", 0.02);
        client.addNoulAnswer("assign-roles.roles.EDITOR", 0.97);
        client.addNoulAnswer("assign-roles.roles.VIEWER", 0.96);
        client.addNoulAnswer("assign-roles.roles.SUPPORT", 0.02);
        client.addNoulAnswer("assign-roles.roles.READONLY", 0.02);

        DispatchResult result = pipeline(client).route("give Alice editor and viewer access", ActionRegistry.getInstance());

        assertEquals(DispatchResult.Status.EXECUTED, result.getStatus());
        assertEquals("[EDITOR, VIEWER]", json(result.getResult()).get("roles").toString());
    }

    @Test
    void ticketNumbersAreBoundAsIntegers() throws Exception {
        MockTypesafeClient client = new MockTypesafeClient();
        client.addChoiceAnswer("__tool__", "escalate-ticket", 0.95);
        client.addChoiceAnswer("escalate-ticket.ticketId", "42", 0.97);

        DispatchResult result = pipeline(client).route("escalate ticket 42", ActionRegistry.getInstance());

        assertEquals(DispatchResult.Status.EXECUTED, result.getStatus());
        assertEquals("42", json(result.getResult()).get("ticketId").toString());
    }

    @Test
    void aNonNumericTicketNumberIsRefusedBeforeAnythingRuns() throws Exception {
        MockTypesafeClient client = new MockTypesafeClient();
        client.addChoiceAnswer("__tool__", "close-ticket", 0.95);
        client.addChoiceAnswer("close-ticket.ticketId", "soon", 0.97);

        assertEquals(DispatchResult.Status.REJECTED,
                pipeline(client).route("close ticket soon", ActionRegistry.getInstance()).getStatus());
    }

    @Test
    void deleteActionsAreNeverExecutedWithoutConfirmation() throws Exception {
        MockTypesafeClient client = new MockTypesafeClient();
        client.addChoiceAnswer("__tool__", "delete-user", 0.99);
        client.addChoiceAnswer("delete-user.name", "Bob", 0.99);

        // No confirmation service in this pipeline, so a confirm-action fails closed.
        DispatchResult result = pipeline(client).route("delete user Bob", ActionRegistry.getInstance());

        assertEquals(DispatchResult.Status.REJECTED, result.getStatus());
    }

    @Test
    void deleteUserNeedsMoreConfidenceThanTheOthers() throws Exception {
        MockTypesafeClient client = new MockTypesafeClient();
        client.addChoiceAnswer("__tool__", "delete-user", 0.90); // fine globally (0.80), not for delete-user (0.95)
        client.addChoiceAnswer("delete-user.name", "Bob", 0.99);

        assertThrows(AmbiguousIntentException.class,
                () -> pipeline(client).route("delete user Bob", ActionRegistry.getInstance()));
    }
}
