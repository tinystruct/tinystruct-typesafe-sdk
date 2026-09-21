package org.tinystruct.typesafe.core.pipeline;

import org.tinystruct.ApplicationException;
import org.tinystruct.typesafe.client.RoutingRequest;
import org.tinystruct.typesafe.client.RoutingResult;
import org.tinystruct.typesafe.client.TypesafeClient;
import org.tinystruct.typesafe.core.candidate.CandidateExtractor;
import org.tinystruct.typesafe.core.catalog.ActionDefinition;
import org.tinystruct.typesafe.core.question.QuestionGenerator;

import java.util.List;

/** One request carrying every question: the documented TypeSafe pattern, and the cheapest in round trips. */
public final class SingleStageClassifier implements Classifier {

    private final TypesafeClient client;
    private final CandidateExtractor extractor;
    private final String model;

    public SingleStageClassifier(TypesafeClient client, CandidateExtractor extractor, String model) {
        this.client = client;
        this.extractor = extractor;
        this.model = model;
    }

    @Override
    public RoutingResult classify(String input, List<ActionDefinition> actions) throws ApplicationException {
        return client.classify(new RoutingRequest(input,
                QuestionGenerator.buildAllQuestions(actions, extractor, input), model));
    }
}
