package org.tinystruct.typesafe.core.policy;

import org.tinystruct.data.component.Builder;
import org.tinystruct.typesafe.client.RoutingResult;
import org.tinystruct.typesafe.core.config.TypesafeConfig;
import org.tinystruct.typesafe.core.question.QuestionGenerator;

import java.util.List;
import java.util.Map;

/**
 * Computes the overall {@link ConfidenceScore} of a routing result and decides what it allows.
 *
 * <p>The overall score is the weakest link across {@code __tool__} and every argument question
 * that was used, never a product (TypeSafe's documented pattern). A choice answer contributes its
 * {@code confidence}; a noul answer, which has none, contributes its decisiveness {@code max(p, 1-p)}.
 * Thresholds do not transfer between the two kinds, so they stay configurable.
 */
public final class ConfidencePolicy {

    /** What a score allows. */
    public enum Tier {
        /** At or above the auto threshold (or above the minimum when there is no auto threshold): run. */
        ACT,
        /** At or above the minimum but below the auto threshold: ask for confirmation. */
        CONFIRM,
        /** Below the minimum: do not act. */
        AMBIGUOUS
    }

    private final double minConfidence;
    private final double autoConfidence;
    private final double setThreshold;
    private final Map<String, Double> minConfidencePerAction;

    /**
     * @param minConfidence          scores below this are ambiguous
     * @param autoConfidence         scores from the minimum up to this need confirmation; {@code NaN} disables the tier
     * @param setThreshold           a set member is included when its noul probability is at least this
     * @param minConfidencePerAction per-action overrides of the minimum, keyed by action path
     */
    public ConfidencePolicy(double minConfidence, double autoConfidence, double setThreshold,
                            Map<String, Double> minConfidencePerAction) {
        this.minConfidence = minConfidence;
        this.autoConfidence = autoConfidence;
        this.setThreshold = setThreshold;
        this.minConfidencePerAction = Map.copyOf(minConfidencePerAction);
    }

    public ConfidencePolicy(double minConfidence, double autoConfidence, double setThreshold) {
        this(minConfidence, autoConfidence, setThreshold, Map.of());
    }

    /** Reads the thresholds, and any {@code typesafe.routing.min-confidence.<action>} overrides, from configuration. */
    public static ConfidencePolicy from(org.tinystruct.system.Configuration<String> config) {
        Map<String, Double> overrides = new java.util.HashMap<>();
        for (String key : config.propertyNames()) {
            if (key.startsWith(TypesafeConfig.MIN_CONFIDENCE_PREFIX)) {
                String action = key.substring(TypesafeConfig.MIN_CONFIDENCE_PREFIX.length());
                double value = TypesafeConfig.dbl(config.get(key), Double.NaN);
                if (!action.isEmpty() && !Double.isNaN(value)) overrides.put(action, value);
            }
        }
        return new ConfidencePolicy(
                TypesafeConfig.dbl(config.get(TypesafeConfig.MIN_CONFIDENCE), 0.80),
                TypesafeConfig.dbl(config.get(TypesafeConfig.AUTO_CONFIDENCE), Double.NaN),
                TypesafeConfig.dbl(config.get(TypesafeConfig.SET_THRESHOLD), 0.5),
                overrides);
    }

    public double getMinConfidence() { return minConfidence; }
    public double getAutoConfidence() { return autoConfidence; }
    public double getSetThreshold() { return setThreshold; }

    /** The minimum confidence for an action, honouring a per-action override. */
    public double minConfidenceFor(String actionPath) {
        Double override = actionPath == null ? null : minConfidencePerAction.get(actionPath);
        return override != null ? override : minConfidence;
    }

    /** Weakest-link confidence over {@code __tool__} and the given argument questions. */
    public ConfidenceScore computeOverall(RoutingResult result, List<String> relevantQuestions) {
        double weakest = result.getChoiceConfidence(QuestionGenerator.TOOL_KEY);
        String weakestQuestion = QuestionGenerator.TOOL_KEY;

        for (String key : relevantQuestions) {
            if (!(result.getAnswers().get(key) instanceof Builder answer)) continue;

            double confidence;
            if (answer.containsKey("confidence")) {
                confidence = result.getChoiceConfidence(key);
            } else if (answer.containsKey("noul")) {
                double p = result.getNoul(key);
                confidence = Math.max(p, 1.0 - p);
            } else {
                continue;
            }
            if (confidence < weakest) {
                weakest = confidence;
                weakestQuestion = key;
            }
        }
        return new ConfidenceScore(weakest, weakestQuestion);
    }

    public Tier classify(ConfidenceScore score, String actionPath) {
        double value = score.getValue();
        if (value < minConfidenceFor(actionPath)) return Tier.AMBIGUOUS;
        if (!Double.isNaN(autoConfidence) && value < autoConfidence) return Tier.CONFIRM;
        return Tier.ACT;
    }

    public Tier classify(ConfidenceScore score) {
        return classify(score, null);
    }
}
