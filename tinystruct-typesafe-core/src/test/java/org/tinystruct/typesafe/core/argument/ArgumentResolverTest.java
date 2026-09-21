package org.tinystruct.typesafe.core.argument;

import org.junit.jupiter.api.Test;
import org.tinystruct.system.annotation.Action.Mode;
import org.tinystruct.typesafe.client.RoutingRequest;
import org.tinystruct.typesafe.client.RoutingResult;
import org.tinystruct.typesafe.client.testing.MockTypesafeClient;
import org.tinystruct.typesafe.core.candidate.TokenSpanExtractor;
import org.tinystruct.typesafe.core.catalog.ActionDefinition;
import org.tinystruct.typesafe.core.catalog.ParameterDefinition;
import org.tinystruct.typesafe.core.question.QuestionGenerator;
import org.tinystruct.typesafe.core.testing.TestApps;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ArgumentResolverTest {

    private static final String INPUT = "make John an admin";
    private final ArgumentResolver resolver = new ArgumentResolver(0.5, new TokenSpanExtractor());

    private static ParameterDefinition text(String name, boolean optional) {
        return new ParameterDefinition(name, "", ParameterDefinition.Kind.OPEN_VALUE, optional, String.class, String.class, null);
    }

    private static ParameterDefinition role(boolean optional) {
        return new ParameterDefinition("role", "", ParameterDefinition.Kind.ENUM_CHOICE, optional,
                TestApps.Role.class, TestApps.Role.class, TestApps.Role.class);
    }

    private static ActionDefinition createUser() {
        return new ActionDefinition("create-user", "d", List.of(text("name", false), role(false)), Mode.DEFAULT);
    }

    private static RoutingResult result(MockTypesafeClient mock) throws Exception {
        return mock.classify(new RoutingRequest("x", new org.tinystruct.data.component.Builder(), "m"));
    }

    @Test
    void resolvesEnumAndSpanValues() throws Exception {
        MockTypesafeClient mock = new MockTypesafeClient();
        mock.addChoiceAnswer("create-user.name", "John", 0.95);
        mock.addChoiceAnswer("create-user.role", "ADMIN", 0.9);

        ArgumentResolver.Resolution r = resolver.resolve(createUser(), result(mock), INPUT, "create-user");

        assertEquals("John", r.values().get("name"));
        assertEquals(TestApps.Role.ADMIN, r.values().get("role"));
        assertTrue(r.unresolved().isEmpty());
        assertEquals(List.of("create-user.name", "create-user.role"), r.usedQuestions());
    }

    @Test
    void aRequiredParameterAnsweredNoneIsReportedUnresolved() throws Exception {
        MockTypesafeClient mock = new MockTypesafeClient();
        mock.addChoiceAnswer("create-user.name", QuestionGenerator.NONE_KEY, 0.9);
        mock.addChoiceAnswer("create-user.role", "ADMIN", 0.9);

        ArgumentResolver.Resolution r = resolver.resolve(createUser(), result(mock), INPUT, "create-user");

        assertEquals(List.of("name"), r.unresolved());
        assertNull(r.values().get("name"));
    }

    @Test
    void aMissingAnswerFailsClosedInsteadOfBeingReadAsADefault() throws Exception {
        MockTypesafeClient mock = new MockTypesafeClient();
        mock.addChoiceAnswer("create-user.role", "ADMIN", 0.9); // name is missing

        InvalidActionException e = assertThrows(InvalidActionException.class,
                () -> resolver.resolve(createUser(), result(mock), INPUT, "create-user"));
        assertTrue(e.getMessage().contains("create-user.name"));
    }

    @Test
    void anInvalidEnumConstantFromTheModelIsRejected() throws Exception {
        MockTypesafeClient mock = new MockTypesafeClient();
        mock.addChoiceAnswer("create-user.name", "John", 0.9);
        mock.addChoiceAnswer("create-user.role", "SUPERUSER", 0.9);

        assertThrows(InvalidActionException.class,
                () -> resolver.resolve(createUser(), result(mock), INPUT, "create-user"));
    }

    @Test
    void aValueThatIsNotASpanOfTheInputIsRejected() throws Exception {
        MockTypesafeClient mock = new MockTypesafeClient();
        mock.addChoiceAnswer("create-user.name", "Mallory; drop table users", 0.99);
        mock.addChoiceAnswer("create-user.role", "ADMIN", 0.9);

        InvalidActionException e = assertThrows(InvalidActionException.class,
                () -> resolver.resolve(createUser(), result(mock), INPUT, "create-user"));
        assertTrue(e.getMessage().contains("not part of the input"));
    }

    @Test
    void flagIsTrueAtOrAboveHalf() throws Exception {
        ParameterDefinition flag = new ParameterDefinition("active", "", ParameterDefinition.Kind.FLAG, false,
                boolean.class, boolean.class, null);
        ActionDefinition def = new ActionDefinition("set-active", "d", List.of(flag), Mode.DEFAULT);

        MockTypesafeClient yes = new MockTypesafeClient();
        yes.addNoulAnswer("set-active.active", 0.5);
        assertEquals(true, resolver.resolve(def, result(yes), INPUT, "set-active").values().get("active"));

        MockTypesafeClient no = new MockTypesafeClient();
        no.addNoulAnswer("set-active.active", 0.49);
        assertEquals(false, resolver.resolve(def, result(no), INPUT, "set-active").values().get("active"));
    }

    @Test
    void setIncludesMembersAtOrAboveTheThreshold() throws Exception {
        ParameterDefinition roles = new ParameterDefinition("roles", "", ParameterDefinition.Kind.SET_ENUM, false,
                Set.class, Set.class, TestApps.Role.class);
        ActionDefinition def = new ActionDefinition("assign-roles", "d", List.of(roles), Mode.DEFAULT);
        MockTypesafeClient mock = new MockTypesafeClient();
        mock.addNoulAnswer("assign-roles.roles.ADMIN", 0.9);
        mock.addNoulAnswer("assign-roles.roles.EDITOR", 0.49);
        mock.addNoulAnswer("assign-roles.roles.VIEWER", 0.5);

        Object value = resolver.resolve(def, result(mock), INPUT, "assign-roles").values().get("roles");

        assertEquals(Set.of(TestApps.Role.ADMIN, TestApps.Role.VIEWER), value);
    }

    @Test
    void aListParameterResolvesToAList() throws Exception {
        ParameterDefinition tags = new ParameterDefinition("tags", "", ParameterDefinition.Kind.SET_ENUM, false,
                List.class, List.class, TestApps.Role.class);
        ActionDefinition def = new ActionDefinition("tag-user", "d", List.of(tags), Mode.DEFAULT);
        MockTypesafeClient mock = new MockTypesafeClient();
        for (TestApps.Role r : TestApps.Role.values()) mock.addNoulAnswer("tag-user.tags." + r, r == TestApps.Role.ADMIN ? 0.9 : 0.1);

        Object value = resolver.resolve(def, result(mock), INPUT, "tag-user").values().get("tags");

        assertEquals(List.of(TestApps.Role.ADMIN), value);
    }

    @Test
    void anOptionalParameterThatWasNotStatedIsNullAndItsChoiceIsNotConsulted() throws Exception {
        ActionDefinition def = new ActionDefinition("create-customer", "d",
                List.of(text("name", false), text("email", true)), Mode.DEFAULT);
        MockTypesafeClient mock = new MockTypesafeClient();
        mock.addChoiceAnswer("create-customer.name", "John", 0.9);
        mock.addNoulAnswer("create-customer.email?", 0.1);

        ArgumentResolver.Resolution r = resolver.resolve(def, result(mock), INPUT, "create-customer");

        assertNull(r.values().get("email"));
        assertTrue(r.unresolved().isEmpty());
        assertFalse(r.usedQuestions().contains("create-customer.email"));
    }

    @Test
    void anOptionalParameterThatWasStatedIsResolved() throws Exception {
        ActionDefinition def = new ActionDefinition("create-customer", "d",
                List.of(text("name", false), text("email", true)), Mode.DEFAULT);
        MockTypesafeClient mock = new MockTypesafeClient();
        mock.addChoiceAnswer("create-customer.name", "John", 0.9);
        mock.addNoulAnswer("create-customer.email?", 0.95);
        mock.addChoiceAnswer("create-customer.email", "an", 0.9);

        assertEquals("an", resolver.resolve(def, result(mock), INPUT, "create-customer").values().get("email"));
    }

    @Test
    void anOptionalParameterAnsweredNoneIsNotUnresolved() throws Exception {
        ActionDefinition def = new ActionDefinition("create-customer", "d",
                List.of(text("name", false), text("email", true)), Mode.DEFAULT);
        MockTypesafeClient mock = new MockTypesafeClient();
        mock.addChoiceAnswer("create-customer.name", "John", 0.9);
        mock.addNoulAnswer("create-customer.email?", 0.95);
        mock.addChoiceAnswer("create-customer.email", QuestionGenerator.NONE_KEY, 0.9);

        ArgumentResolver.Resolution r = resolver.resolve(def, result(mock), INPUT, "create-customer");
        assertTrue(r.unresolved().isEmpty());
        assertNull(r.values().get("email"));
    }
}
