package org.tinystruct.typesafe.core.policy;

/**
 * The overall routing confidence, computed as the weakest-link across {@code __tool__}
 * and all argument questions.
 *
 * <p>For choice questions, TypeSafe''s {@code confidence} field is used directly.
 * For noul questions there is no confidence field; we use {@code max(p, 1−p)} as the
 * decisiveness measure.
 */
public final class ConfidenceScore {

    private final double value;
    private final String weakestQuestion;

    public ConfidenceScore(double value, String weakestQuestion) {
        this.value = Math.max(0.0, Math.min(1.0, value));
        this.weakestQuestion = weakestQuestion;
    }

    /** The weakest-link confidence across all relevant questions (0.0–1.0). */
    public double getValue() { return value; }

    /** The question ID that produced the lowest confidence. */
    public String getWeakestQuestion() { return weakestQuestion; }

    @Override
    public String toString() {
        return String.format("ConfidenceScore{%.4f, weakest=%s}", value, weakestQuestion);
    }
}
