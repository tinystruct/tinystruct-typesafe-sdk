package org.tinystruct.typesafe.core.pipeline;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.tinystruct.application.ActionRegistry;
import org.tinystruct.data.component.Builder;
import org.tinystruct.system.annotation.Action.Mode;
import org.tinystruct.typesafe.client.RoutingRequest;
import org.tinystruct.typesafe.client.testing.MockTypesafeClient;
import org.tinystruct.typesafe.core.argument.ArgumentResolver;
import org.tinystruct.typesafe.core.argument.ArgumentValidator;
import org.tinystruct.typesafe.core.candidate.TokenSpanExtractor;
import org.tinystruct.typesafe.core.catalog.ActionCatalog;
import org.tinystruct.typesafe.core.policy.ConfidencePolicy;
import org.tinystruct.typesafe.core.question.QuestionGenerator;
import org.tinystruct.typesafe.core.testing.TestApps;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The interpreter on its own: a stub classifier stands in for TypeSafe, so these tests show that
 * understanding an instruction needs no client, cache, executor or confirmation service.
 */
class InterpreterTest {

    private static final String INPUT = "make John an admin";

    private final MockTypesafeClient answers = new MockTypesafeClient();
    private final AtomicInteger classifications = new AtomicInteger();

    @BeforeAll
    static void install() {
        TestApps.install();
    }

    private Interpreter interpreter(Set<String> allowed) {
        Classifier stub = (input, actions) -> {
            classifications.incrementAndGet();
            return answers.classify(new RoutingRequest(input, new Builder(), "m"));
        };
        TokenSpanExtractor extractor = new TokenSpanExtractor();
        ConfidencePolicy policy = new ConfidencePolicy(0.80, Double.NaN, 0.5);
        return new Interpreter(new ActionCatalog(allowed), stub, new ArgumentResolver(0.5, extractor),
                new ArgumentValidator(), policy);
    }

    private Interpretation interpret(Set<String> allowed, Mode mode) throws Exception {
        return interpreter(allowed).interpret(INPUT, ActionRegistry.getInstance(), mode);
    }

    private void answerCreateUser() {
        answers.addChoiceAnswer(QuestionGenerator.TOOL_KEY, "create-user", 0.95);
        answers.addChoiceAnswer("create-user.name", "John", 0.97);
        answers.addChoiceAnswer("create-user.role", "ADMIN", 0.96);
    }

    @Test
    void understandsAnInstructionAsAValidatedCall() throws Exception {
        answerCreateUser();

        Interpretation result = interpret(Set.of("create-user"), Mode.CLI);

        assertFalse(result.isRefused());
        assertEquals("create-user", result.action().getActionPath());
        assertEquals("John", result.arguments().get("name"));
        assertEquals(TestApps.Role.ADMIN, result.arguments().get("role"));
        assertEquals(0.95, result.score().getValue(), 1e-9, "the weakest answer decides");
        assertNull(TestApps.Calls.last, "interpreting never runs anything");
    }

    @Test
    void nothingAllowlistedMeansTypeSafeIsNeverAsked() throws Exception {
        Interpretation result = interpret(Set.of(), Mode.CLI);

        assertTrue(result.isRefused());
        assertEquals(0, classifications.get());
    }

    @Test
    void noActionAvailableInThisModeMeansTypeSafeIsNeverAsked() throws Exception {
        Interpretation result = interpret(Set.of("post-only"), Mode.CLI);

        assertTrue(result.isRefused());
        assertEquals(0, classifications.get());
    }

    @Test
    void otherIsARefusalWithoutAnAction() throws Exception {
        answers.addChoiceAnswer(QuestionGenerator.TOOL_KEY, QuestionGenerator.OTHER_KEY, 0.95);

        Interpretation result = interpret(Set.of("create-user"), Mode.CLI);

        assertTrue(result.isRefused());
        assertNull(result.action());
    }

    @Test
    void anActionThatWasNotOfferedIsRefused() throws Exception {
        answers.addChoiceAnswer(QuestionGenerator.TOOL_KEY, "delete-user", 0.99);

        Interpretation result = interpret(Set.of("create-user"), Mode.CLI);

        assertTrue(result.isRefused());
        assertNull(result.action());
    }

    @Test
    void aMissingActionAnswerIsARefusal() throws Exception {
        Interpretation result = interpret(Set.of("create-user"), Mode.CLI);

        assertTrue(result.isRefused());
        assertTrue(result.refusal().contains("action selection"));
    }

    @Test
    void aRefusalAfterTheChoiceStillRemembersTheChosenAction() throws Exception {
        answers.addChoiceAnswer(QuestionGenerator.TOOL_KEY, "create-user", 0.95);
        answers.addChoiceAnswer("create-user.name", QuestionGenerator.NONE_KEY, 0.95);
        answers.addChoiceAnswer("create-user.role", "ADMIN", 0.95);

        Interpretation result = interpret(Set.of("create-user"), Mode.CLI);

        assertTrue(result.isRefused());
        assertTrue(result.refusal().contains("name"));
        assertEquals("create-user", result.action().getActionPath(), "so the model's choice can still be counted");
    }

    @Test
    void anInvalidArgumentIsRefusedNotThrown() throws Exception {
        answers.addChoiceAnswer(QuestionGenerator.TOOL_KEY, "create-user", 0.95);
        answers.addChoiceAnswer("create-user.name", "John", 0.95);
        answers.addChoiceAnswer("create-user.role", "ROOT", 0.95);

        Interpretation result = interpret(Set.of("create-user"), Mode.CLI);

        assertTrue(result.isRefused());
        assertEquals("create-user", result.action().getActionPath());
    }

    @Test
    void aMissingArgumentAnswerIsARefusal() throws Exception {
        answers.addChoiceAnswer(QuestionGenerator.TOOL_KEY, "create-user", 0.95);
        answers.addChoiceAnswer("create-user.name", "John", 0.95); // role has no answer

        assertTrue(interpret(Set.of("create-user"), Mode.CLI).isRefused());
    }

    @Test
    void theClassifierIsCalledOncePerInterpretation() throws Exception {
        answerCreateUser();
        interpret(Set.of("create-user"), Mode.CLI);
        assertEquals(1, classifications.get());
    }
}
