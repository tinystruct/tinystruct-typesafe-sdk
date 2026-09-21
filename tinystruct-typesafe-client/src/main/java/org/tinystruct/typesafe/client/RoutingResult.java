package org.tinystruct.typesafe.client;

import org.tinystruct.ApplicationException;
import org.tinystruct.data.component.Builder;

/**
 * Parsed response from the TypeSafe {@code /v1/systemone} endpoint.
 *
 * <p>{@code answers} maps a question id to its answer: {@code choice} + {@code probabilities} +
 * {@code confidence} for choice questions, {@code noul} for yes/no questions. {@code modelVersion}
 * is the exact model string TypeSafe reports, and {@code usage} holds token counters.
 */
public class RoutingResult {

    private final Builder answers;
    private final String modelVersion;
    private final Builder usage;

    public RoutingResult(Builder answers, String modelVersion, Builder usage) {
        this.answers = answers == null ? new Builder() : answers;
        this.modelVersion = modelVersion;
        this.usage = usage == null ? new Builder() : usage;
    }

    /** Parses the JSON body of a {@code /v1/systemone} response. */
    public static RoutingResult parse(String json, String fallbackModel) throws ApplicationException {
        Builder root = new Builder();
        root.parse(json);
        Builder answers = asBuilder(root.get("answers"));
        Builder usage = asBuilder(root.get("usage"));
        String model = root.containsKey("model") && root.get("model") != null
                ? root.get("model").toString() : fallbackModel;
        return new RoutingResult(answers, model, usage);
    }

    /** Serialises to the same shape {@link #parse} reads, so a result can be cached as text. */
    public String toJson() {
        Builder root = new Builder();
        root.put("model", modelVersion);
        root.put("answers", answers);
        root.put("usage", usage);
        return root.toString();
    }

    private static Builder asBuilder(Object value) throws ApplicationException {
        if (value instanceof Builder b) return b;
        Builder b = new Builder();
        if (value != null) b.parse(value.toString());
        return b;
    }

    /** All answers, keyed by question id. */
    public Builder getAnswers() { return answers; }

    /** Exact model string returned by TypeSafe. */
    public String getModelVersion() { return modelVersion; }

    /** Token usage counters reported by TypeSafe. */
    public Builder getUsage() { return usage; }

    /** {@code true} if there is an answer for the question. */
    public boolean hasAnswer(String questionId) {
        return answers.get(questionId) instanceof Builder;
    }

    /** {@code true} if the question has a {@code noul} answer. */
    public boolean hasNoul(String questionId) {
        return answers.get(questionId) instanceof Builder a && a.get("noul") != null;
    }

    /** The {@code confidence} of a choice answer (how peaked its distribution is), or 0.0 if absent. */
    public double getChoiceConfidence(String questionId) {
        return number(questionId, "confidence", 0.0);
    }

    /** The chosen option of a choice question, or {@code null} if absent. */
    public String getChoice(String questionId) {
        if (!(answers.get(questionId) instanceof Builder a)) return null;
        Object choice = a.get("choice");
        return choice != null ? choice.toString() : null;
    }

    /** The yes-probability (0.0 to 1.0) of a noul question, or 0.5 if absent. */
    public double getNoul(String questionId) {
        return number(questionId, "noul", 0.5);
    }

    private double number(String questionId, String field, double absent) {
        if (!(answers.get(questionId) instanceof Builder a)) return absent;
        Object value = a.get(field);
        if (value == null) return absent;
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return absent;
        }
    }
}
