package org.tinystruct.typesafe.core.pipeline;

import org.tinystruct.ApplicationException;
import org.tinystruct.data.component.Builder;
import org.tinystruct.typesafe.client.RoutingRequest;
import org.tinystruct.typesafe.client.RoutingResult;
import org.tinystruct.typesafe.client.TypesafeClient;
import org.tinystruct.typesafe.core.candidate.CandidateExtractor;
import org.tinystruct.typesafe.core.catalog.ActionDefinition;
import org.tinystruct.typesafe.core.question.QuestionGenerator;

import java.util.List;

/**
 * The action question first, then only the chosen action's argument questions. Two round trips, but
 * far smaller requests when many actions are allowlisted (the argument questions of every action
 * would otherwise travel in every request).
 */
public final class TwoStageClassifier implements Classifier {

    private final TypesafeClient client;
    private final CandidateExtractor extractor;
    private final String model;

    public TwoStageClassifier(TypesafeClient client, CandidateExtractor extractor, String model) {
        this.client = client;
        this.extractor = extractor;
        this.model = model;
    }

    @Override
    public RoutingResult classify(String input, List<ActionDefinition> actions) throws ApplicationException {
        Builder toolOnly = new Builder();
        toolOnly.put(QuestionGenerator.TOOL_KEY, QuestionGenerator.buildToolQuestion(actions));
        RoutingResult first = client.classify(new RoutingRequest(input, toolOnly, model));

        ActionDefinition chosen = find(actions, first.getChoice(QuestionGenerator.TOOL_KEY));
        if (chosen == null) return first; // "other", or not an offered action: the interpreter handles both

        Builder arguments = new Builder();
        QuestionGenerator.buildArgumentQuestions(chosen, extractor, input, arguments);
        if (arguments.keySet().isEmpty()) return first;

        RoutingResult second = client.classify(new RoutingRequest(input, arguments, model));
        Builder merged = new Builder();
        for (String key : first.getAnswers().keySet()) merged.put(key, first.getAnswers().get(key));
        for (String key : second.getAnswers().keySet()) merged.put(key, second.getAnswers().get(key));
        return new RoutingResult(merged, second.getModelVersion(), second.getUsage());
    }

    private static ActionDefinition find(List<ActionDefinition> actions, String questionKey) {
        for (ActionDefinition action : actions) {
            if (QuestionGenerator.escape(action.getActionPath()).equals(questionKey)) return action;
        }
        return null;
    }
}
