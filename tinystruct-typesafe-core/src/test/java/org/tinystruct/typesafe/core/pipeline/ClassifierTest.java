package org.tinystruct.typesafe.core.pipeline;

import org.junit.jupiter.api.Test;
import org.tinystruct.ApplicationException;
import org.tinystruct.data.component.Builder;
import org.tinystruct.system.annotation.Action.Mode;
import org.tinystruct.typesafe.client.RoutingRequest;
import org.tinystruct.typesafe.client.RoutingResult;
import org.tinystruct.typesafe.client.TypesafeClient;
import org.tinystruct.typesafe.client.testing.MockTypesafeClient;
import org.tinystruct.typesafe.core.candidate.TokenSpanExtractor;
import org.tinystruct.typesafe.core.catalog.ActionDefinition;
import org.tinystruct.typesafe.core.catalog.ParameterDefinition;
import org.tinystruct.typesafe.core.question.QuestionGenerator;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** How many requests each classification strategy sends, and what it puts in them. */
class ClassifierTest {

    private static final String INPUT = "make John an admin";

    /** Records every request and answers from a mock. */
    private static final class Recording implements TypesafeClient {
        final MockTypesafeClient mock = new MockTypesafeClient();
        final List<RoutingRequest> requests = new ArrayList<>();

        @Override
        public RoutingResult classify(RoutingRequest request) throws ApplicationException {
            requests.add(request);
            return mock.classify(request);
        }
    }

    private static ParameterDefinition text(String name) {
        return new ParameterDefinition(name, "the " + name, ParameterDefinition.Kind.OPEN_VALUE, false,
                String.class, String.class, null);
    }

    private static ActionDefinition createUser() {
        return new ActionDefinition("create-user", "Create a user.", List.of(text("name")), Mode.DEFAULT);
    }

    private static ActionDefinition deleteUser() {
        return new ActionDefinition("delete-user", "Delete a user.", List.of(text("name")), Mode.DEFAULT);
    }

    private static ActionDefinition ping() {
        return new ActionDefinition("ping", "Ping.", List.of(), Mode.DEFAULT);
    }

    @Test
    void singleStageSendsEveryQuestionInOneRequest() throws Exception {
        Recording client = new Recording();
        client.mock.addChoiceAnswer(QuestionGenerator.TOOL_KEY, "create-user", 0.9);

        new SingleStageClassifier(client, new TokenSpanExtractor(), "jev-x")
                .classify(INPUT, List.of(createUser(), deleteUser()));

        assertEquals(1, client.requests.size());
        RoutingRequest sent = client.requests.get(0);
        assertEquals("jev-x", sent.getModel());
        assertEquals(INPUT, sent.getState());
        Builder questions = sent.getQuestions();
        assertTrue(questions.containsKey("__tool__"));
        assertTrue(questions.containsKey("create-user.name"));
        assertTrue(questions.containsKey("delete-user.name"));
    }

    @Test
    void twoStageAsksForTheActionFirstThenOnlyItsArguments() throws Exception {
        Recording client = new Recording();
        client.mock.addChoiceAnswer(QuestionGenerator.TOOL_KEY, "create-user", 0.9);
        client.mock.addChoiceAnswer("create-user.name", "John", 0.9);

        RoutingResult merged = new TwoStageClassifier(client, new TokenSpanExtractor(), "jev-x")
                .classify(INPUT, List.of(createUser(), deleteUser()));

        assertEquals(2, client.requests.size());
        assertEquals(List.of("__tool__"), new ArrayList<>(client.requests.get(0).getQuestions().keySet()));
        Builder second = client.requests.get(1).getQuestions();
        assertTrue(second.containsKey("create-user.name"));
        assertFalse(second.containsKey("delete-user.name"), "arguments of actions that were not chosen are never sent");
        assertFalse(second.containsKey("__tool__"));
        assertEquals("create-user", merged.getChoice("__tool__"), "the merged result carries both stages");
        assertEquals("John", merged.getChoice("create-user.name"));
    }

    @Test
    void twoStageStopsAfterTheFirstRequestWhenNothingMatches() throws Exception {
        Recording client = new Recording();
        client.mock.addChoiceAnswer(QuestionGenerator.TOOL_KEY, QuestionGenerator.OTHER_KEY, 0.9);

        new TwoStageClassifier(client, new TokenSpanExtractor(), "m").classify(INPUT, List.of(createUser()));

        assertEquals(1, client.requests.size());
    }

    @Test
    void twoStageStopsAfterTheFirstRequestWhenTheModelInventsAnAction() throws Exception {
        Recording client = new Recording();
        client.mock.addChoiceAnswer(QuestionGenerator.TOOL_KEY, "not-offered", 0.9);

        new TwoStageClassifier(client, new TokenSpanExtractor(), "m").classify(INPUT, List.of(createUser()));

        assertEquals(1, client.requests.size());
    }

    @Test
    void twoStageNeedsNoSecondRequestForAnActionWithoutArguments() throws Exception {
        Recording client = new Recording();
        client.mock.addChoiceAnswer(QuestionGenerator.TOOL_KEY, "ping", 0.9);

        new TwoStageClassifier(client, new TokenSpanExtractor(), "m").classify("ping", List.of(ping()));

        assertEquals(1, client.requests.size());
    }

    @Test
    void failuresPropagateUnchanged() {
        Recording client = new Recording();
        client.mock.failWith(new ApplicationException("down"));

        assertThrows(ApplicationException.class,
                () -> new SingleStageClassifier(client, new TokenSpanExtractor(), "m").classify(INPUT, List.of(createUser())));
    }
}
