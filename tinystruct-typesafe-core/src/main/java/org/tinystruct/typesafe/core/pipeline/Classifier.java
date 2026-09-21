package org.tinystruct.typesafe.core.pipeline;

import org.tinystruct.ApplicationException;
import org.tinystruct.typesafe.client.RoutingResult;
import org.tinystruct.typesafe.core.catalog.ActionDefinition;

import java.util.List;

/**
 * Asks TypeSafe which action the input means and what its arguments are.
 *
 * <p>How many requests that takes is the implementation's business ({@link SingleStageClassifier},
 * {@link TwoStageClassifier}); the {@link Interpreter} only sees the merged answers.
 */
public interface Classifier {

    /**
     * @param input   the user's instruction
     * @param actions the routable actions (never empty)
     * @return the answers to the action question and to the arguments of the action it chose
     */
    RoutingResult classify(String input, List<ActionDefinition> actions) throws ApplicationException;
}
