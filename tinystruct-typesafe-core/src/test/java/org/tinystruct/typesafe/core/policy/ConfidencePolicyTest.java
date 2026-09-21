package org.tinystruct.typesafe.core.policy;

import org.junit.jupiter.api.Test;
import org.tinystruct.data.component.Builder;
import org.tinystruct.typesafe.client.RoutingResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfidencePolicyTest {

    private static final double MIN = 0.80;
    private static final double AUTO = 0.90;

    private ConfidencePolicy policy() {
        return new ConfidencePolicy(MIN, AUTO, 0.5);
    }

    @Test
    void belowMinIsAmbiguous() {
        ConfidenceScore score = new ConfidenceScore(0.79, "__tool__");
        assertEquals(ConfidencePolicy.Tier.AMBIGUOUS, policy().classify(score));
    }

    @Test
    void betweenMinAndAutoIsConfirm() {
        ConfidenceScore score = new ConfidenceScore(0.85, "__tool__");
        assertEquals(ConfidencePolicy.Tier.CONFIRM, policy().classify(score));
    }

    @Test
    void aboveAutoIsAct() {
        ConfidenceScore score = new ConfidenceScore(0.95, "__tool__");
        assertEquals(ConfidencePolicy.Tier.ACT, policy().classify(score));
    }

    @Test
    void weakestLinkIsUsed() {
        Builder answers = new Builder();
        answers.put("__tool__", buildChoiceAnswer("create-user", 0.95));
        answers.put("create-user.name", buildChoiceAnswer("John", 0.82));
        answers.put("create-user.role", buildChoiceAnswer("ADMIN", 0.96));
        RoutingResult result = new RoutingResult(answers, "jev-test", new Builder());
        ConfidenceScore score = policy().computeOverall(result,
                List.of("create-user.name", "create-user.role"));
        assertEquals(0.82, score.getValue(), 0.001);
        assertEquals("create-user.name", score.getWeakestQuestion());
    }

    @Test
    void noulDecisivenessMaxP1MinusP() {
        Builder answers = new Builder();
        answers.put("__tool__", buildChoiceAnswer("some-action", 0.95));
        Builder noulAnswer = new Builder();
        noulAnswer.put("noul", 0.92);
        answers.put("my-action.flag", noulAnswer);
        RoutingResult result = new RoutingResult(answers, "jev-test", new Builder());
        ConfidenceScore score = policy().computeOverall(result, List.of("my-action.flag"));
        assertEquals(0.92, score.getValue(), 0.001);
    }

    @Test
    void autoConfidenceDisabledMeansActAboveMin() {
        ConfidencePolicy noAuto = new ConfidencePolicy(0.80, Double.NaN, 0.5);
        ConfidenceScore score = new ConfidenceScore(0.85, "__tool__");
        assertEquals(ConfidencePolicy.Tier.ACT, noAuto.classify(score));
    }

    private Builder buildChoiceAnswer(String choice, double confidence) {
        Builder b = new Builder();
        b.put("choice", choice);
        b.put("confidence", confidence);
        return b;
    }
}