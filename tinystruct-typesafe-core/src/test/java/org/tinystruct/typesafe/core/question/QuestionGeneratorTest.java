package org.tinystruct.typesafe.core.question;

import org.junit.jupiter.api.Test;
import org.tinystruct.data.component.Builder;
import org.tinystruct.system.annotation.Action.Mode;
import org.tinystruct.typesafe.core.candidate.CandidateExtractor;
import org.tinystruct.typesafe.core.candidate.TokenSpanExtractor;
import org.tinystruct.typesafe.core.catalog.ActionDefinition;
import org.tinystruct.typesafe.core.catalog.ParameterDefinition;
import org.tinystruct.typesafe.core.testing.TestApps;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The generated questions must match TypeSafe's documented request shape. */
class QuestionGeneratorTest {

    private static final CandidateExtractor SPANS = new TokenSpanExtractor();

    private static ParameterDefinition text(String name, String description, boolean optional) {
        return new ParameterDefinition(name, description, ParameterDefinition.Kind.OPEN_VALUE, optional,
                String.class, String.class, null);
    }

    private static ParameterDefinition role() {
        return new ParameterDefinition("role", "The role", ParameterDefinition.Kind.ENUM_CHOICE, false,
                TestApps.Role.class, TestApps.Role.class, TestApps.Role.class);
    }

    private static ActionDefinition createUser() {
        return new ActionDefinition("create-user", "Create a user account.",
                List.of(text("name", "The user's name", false), role()), Mode.DEFAULT);
    }

    private static Builder child(Builder parent, String key) {
        assertTrue(parent.get(key) instanceof Builder, "missing object: " + key + " in " + parent);
        return (Builder) parent.get(key);
    }

    @Test
    void toolQuestionIsAChoiceWithInstructionsAndACriteriaMap() {
        Builder tool = child(QuestionGenerator.buildAllQuestions(List.of(createUser()), SPANS, "make John an admin"),
                QuestionGenerator.TOOL_KEY);

        assertEquals("choice", tool.get("type").toString());
        assertFalse(tool.get("instructions").toString().isBlank());
        Builder criteria = child(tool, "criteria");
        assertEquals("Create a user account.", criteria.get("create-user").toString());
        assertTrue(criteria.containsKey(QuestionGenerator.OTHER_KEY), "there must be a way to say none of them");
    }

    @Test
    void noQuestionUsesTheOldOptionsListShape() {
        Builder all = QuestionGenerator.buildAllQuestions(List.of(createUser()), SPANS, "make John an admin");
        for (String key : all.keySet()) {
            Builder q = child(all, key);
            assertFalse(q.containsKey("options"), key);
            assertTrue(q.containsKey("instructions"), key + " needs instructions");
            assertTrue(q.containsKey("type"), key);
        }
    }

    @Test
    void enumParameterListsItsConstantsAndANoneOption() {
        Builder all = QuestionGenerator.buildAllQuestions(List.of(createUser()), SPANS, "make John an admin");
        Builder criteria = child(child(all, "create-user.role"), "criteria");

        assertTrue(criteria.containsKey("ADMIN"));
        assertTrue(criteria.containsKey("EDITOR"));
        assertTrue(criteria.containsKey("VIEWER"));
        assertTrue(criteria.containsKey(QuestionGenerator.NONE_KEY));
    }

    @Test
    void openParameterOffersEverySpanOfTheInputAsAnOption() {
        Builder all = QuestionGenerator.buildAllQuestions(List.of(createUser()), SPANS, "make John Smith an admin");
        Builder criteria = child(child(all, "create-user.name"), "criteria");

        assertTrue(criteria.containsKey("John"));
        assertTrue(criteria.containsKey("John Smith"), "multi-token names must be offerable");
        assertTrue(criteria.containsKey(QuestionGenerator.NONE_KEY));
    }

    @Test
    void reservedKeysCanNeverBeOfferedAsUserValues() {
        Builder all = QuestionGenerator.buildAllQuestions(List.of(createUser()), SPANS,
                "make __none__ and __other__ an admin");
        Builder criteria = child(child(all, "create-user.name"), "criteria");

        assertEquals("The user did not say.", criteria.get(QuestionGenerator.NONE_KEY).toString(),
                "the none key must keep its own meaning");
        assertFalse(criteria.containsKey(QuestionGenerator.OTHER_KEY));
    }

    @Test
    void neverMoreThan255Options() {
        StringBuilder input = new StringBuilder();
        for (int i = 0; i < 400; i++) input.append("w").append(i).append(' ');
        Builder all = QuestionGenerator.buildAllQuestions(List.of(createUser()), SPANS, input.toString());
        Builder criteria = child(child(all, "create-user.name"), "criteria");

        assertTrue(criteria.keySet().size() <= QuestionGenerator.MAX_CHOICE_OPTIONS);
        assertTrue(criteria.containsKey(QuestionGenerator.NONE_KEY), "the none slot is always reserved");
    }

    @Test
    void flagIsOneNoulQuestionWithNoCriteria() {
        ParameterDefinition flag = new ParameterDefinition("active", "Whether active", ParameterDefinition.Kind.FLAG,
                false, boolean.class, boolean.class, null);
        Builder all = QuestionGenerator.buildAllQuestions(
                List.of(new ActionDefinition("set-active", "Toggle", List.of(flag), Mode.DEFAULT)), SPANS, "turn it on");

        Builder q = child(all, "set-active.active");
        assertEquals("noul", q.get("type").toString());
        assertFalse(q.containsKey("criteria"));
    }

    @Test
    void setParameterIsOneNoulPerMember() {
        ParameterDefinition roles = new ParameterDefinition("roles", "The roles", ParameterDefinition.Kind.SET_ENUM,
                false, java.util.Set.class, java.util.Set.class, TestApps.Role.class);
        Builder all = QuestionGenerator.buildAllQuestions(
                List.of(new ActionDefinition("assign-roles", "Assign", List.of(roles), Mode.DEFAULT)), SPANS, "x");

        for (TestApps.Role member : TestApps.Role.values()) {
            assertEquals("noul", child(all, "assign-roles.roles." + member.name()).get("type").toString());
        }
    }

    @Test
    void optionalParameterAddsAStatedQuestion() {
        ActionDefinition def = new ActionDefinition("create-customer", "Create",
                List.of(text("name", "Name", false), text("email", "Email", true)), Mode.DEFAULT);
        Builder all = QuestionGenerator.buildAllQuestions(List.of(def), SPANS, "add Al");

        assertTrue(all.containsKey("create-customer.email?"));
        assertFalse(all.containsKey("create-customer.name?"));
        assertEquals("noul", child(all, "create-customer.email?").get("type").toString());
    }

    @Test
    void dotsAndQuestionMarksInNamesAreEscapedInKeys() {
        assertEquals("a__dot__b", QuestionGenerator.escape("a.b"));
        assertEquals("a__q__", QuestionGenerator.escape("a?"));
    }

    @Test
    void twoStagePartsCanBeBuiltSeparately() {
        Builder toolOnly = QuestionGenerator.buildToolQuestion(List.of(createUser()));
        assertEquals("choice", toolOnly.get("type").toString());

        Builder arguments = new Builder();
        QuestionGenerator.buildArgumentQuestions(createUser(), SPANS, "make John an admin", arguments);
        assertTrue(arguments.containsKey("create-user.name"));
        assertFalse(arguments.containsKey(QuestionGenerator.TOOL_KEY));
    }

    @Test
    void instructionsMentionTheActionAndTheParameterDescription() {
        Builder all = QuestionGenerator.buildAllQuestions(List.of(createUser()), SPANS, "make John an admin");
        String instructions = child(all, "create-user.role").get("instructions").toString();
        assertTrue(instructions.contains("create-user"));
        assertTrue(instructions.contains("The role"));
    }
}
