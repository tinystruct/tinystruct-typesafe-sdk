package org.tinystruct.typesafe.core.policy;

import org.tinystruct.ApplicationException;

/**
 * Thrown when the routing confidence is below the configured minimum threshold,
 * meaning the intent cannot be reliably determined.
 */
public class AmbiguousIntentException extends ApplicationException {

    private final double confidence;
    private final double threshold;

    public AmbiguousIntentException(double confidence, double threshold) {
        super(String.format(
                "Intent is ambiguous: confidence %.4f is below the minimum threshold %.4f",
                confidence, threshold));
        this.confidence = confidence;
        this.threshold = threshold;
    }

    public double getConfidence() { return confidence; }
    public double getThreshold() { return threshold; }
}
