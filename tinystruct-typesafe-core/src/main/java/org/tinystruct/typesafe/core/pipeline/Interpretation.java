package org.tinystruct.typesafe.core.pipeline;

import org.tinystruct.typesafe.core.catalog.ActionDefinition;
import org.tinystruct.typesafe.core.policy.ConfidenceScore;

import java.util.Map;

/**
 * What the model's answers amount to: either a validated call (action, typed arguments, confidence)
 * or a refusal with a reason.
 *
 * <p>{@code action} is also set on a refusal that happened after the model had chosen one (for
 * example a missing argument), so the choice can still be counted.
 */
public record Interpretation(ActionDefinition action, Map<String, Object> arguments,
                             ConfidenceScore score, String refusal) {

    static Interpretation matched(ActionDefinition action, Map<String, Object> arguments, ConfidenceScore score) {
        return new Interpretation(action, arguments, score, null);
    }

    static Interpretation refused(String reason) {
        return new Interpretation(null, Map.of(), null, reason);
    }

    static Interpretation refused(String reason, ActionDefinition chosen) {
        return new Interpretation(chosen, Map.of(), null, reason);
    }

    public boolean isRefused() {
        return refusal != null;
    }
}
