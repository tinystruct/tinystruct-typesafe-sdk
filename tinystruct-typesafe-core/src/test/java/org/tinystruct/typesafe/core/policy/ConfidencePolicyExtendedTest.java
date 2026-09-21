package org.tinystruct.typesafe.core.policy;

import org.junit.jupiter.api.Test;
import org.tinystruct.typesafe.client.RoutingRequest;
import org.tinystruct.typesafe.client.RoutingResult;
import org.tinystruct.typesafe.client.testing.MockTypesafeClient;
import org.tinystruct.typesafe.core.config.TypesafeConfig;
import org.tinystruct.typesafe.core.question.QuestionGenerator;
import org.tinystruct.typesafe.core.testing.MapConfiguration;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfidencePolicyExtendedTest {

    @Test
    void perActionOverrideReplacesTheMinimum() {
        ConfidencePolicy policy = new ConfidencePolicy(0.80, Double.NaN, 0.5, Map.of("delete-user", 0.95));

        assertEquals(0.95, policy.minConfidenceFor("delete-user"));
        assertEquals(0.80, policy.minConfidenceFor("create-user"));
        assertEquals(ConfidencePolicy.Tier.AMBIGUOUS, policy.classify(new ConfidenceScore(0.9, "q"), "delete-user"));
        assertEquals(ConfidencePolicy.Tier.ACT, policy.classify(new ConfidenceScore(0.9, "q"), "create-user"));
    }

    @Test
    void readsThresholdsAndOverridesFromConfiguration() {
        MapConfiguration config = new MapConfiguration();
        config.set(TypesafeConfig.MIN_CONFIDENCE, "0.7");
        config.set(TypesafeConfig.AUTO_CONFIDENCE, "0.9");
        config.set(TypesafeConfig.SET_THRESHOLD, "0.6");
        config.set(TypesafeConfig.MIN_CONFIDENCE_PREFIX + "delete-user", "0.97");
        config.set(TypesafeConfig.MIN_CONFIDENCE_PREFIX + "broken", "not-a-number");

        ConfidencePolicy policy = ConfidencePolicy.from(config);

        assertEquals(0.7, policy.getMinConfidence());
        assertEquals(0.9, policy.getAutoConfidence());
        assertEquals(0.6, policy.getSetThreshold());
        assertEquals(0.97, policy.minConfidenceFor("delete-user"));
        assertEquals(0.7, policy.minConfidenceFor("broken"), "an unparseable override is ignored");
    }

    @Test
    void defaultsWhenNothingIsConfigured() {
        ConfidencePolicy policy = ConfidencePolicy.from(new MapConfiguration());
        assertEquals(0.80, policy.getMinConfidence());
        assertTrue(Double.isNaN(policy.getAutoConfidence()));
        assertEquals(0.5, policy.getSetThreshold());
    }

    @Test
    void anUnansweredArgumentQuestionDoesNotLowerTheScore() throws Exception {
        MockTypesafeClient mock = new MockTypesafeClient();
        mock.addChoiceAnswer(QuestionGenerator.TOOL_KEY, "a", 0.93);
        RoutingResult result = mock.classify(new RoutingRequest("x", new org.tinystruct.data.component.Builder(), "m"));

        ConfidenceScore score = new ConfidencePolicy(0.8, Double.NaN, 0.5).computeOverall(result, List.of("a.missing"));

        assertEquals(0.93, score.getValue(), 1e-9);
        assertEquals(QuestionGenerator.TOOL_KEY, score.getWeakestQuestion());
    }

    @Test
    void confidenceScoreIsClampedToUnitRange() {
        assertEquals(1.0, new ConfidenceScore(7, "q").getValue());
        assertEquals(0.0, new ConfidenceScore(-1, "q").getValue());
    }
}
