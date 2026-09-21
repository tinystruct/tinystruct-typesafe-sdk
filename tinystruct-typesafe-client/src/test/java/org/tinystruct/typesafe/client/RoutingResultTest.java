package org.tinystruct.typesafe.client;

import org.junit.jupiter.api.Test;
import org.tinystruct.data.component.Builder;

import static org.junit.jupiter.api.Assertions.*;

class RoutingResultTest {

    private static final String JSON =
            "{\"model\":\"jev-1.13.0\",\"answers\":{"
                    + "\"__tool__\":{\"type\":\"choice\",\"choice\":\"create-user\",\"confidence\":0.91,"
                    + "\"probabilities\":{\"create-user\":0.95,\"__other__\":0.05}},"
                    + "\"create-user.admin\":{\"type\":\"noul\",\"noul\":0.8}},"
                    + "\"usage\":{\"input_tokens\":328,\"output_tokens\":34}}";

    @Test
    void parsesChoiceAndNoulAnswers() throws Exception {
        RoutingResult r = RoutingResult.parse(JSON, "fallback");

        assertEquals("jev-1.13.0", r.getModelVersion());
        assertEquals("create-user", r.getChoice("__tool__"));
        assertEquals(0.91, r.getChoiceConfidence("__tool__"), 1e-9);
        assertEquals(0.8, r.getNoul("create-user.admin"), 1e-9);
        assertTrue(r.hasAnswer("__tool__"));
        assertTrue(r.hasNoul("create-user.admin"));
        assertEquals(328, Integer.parseInt(r.getUsage().get("input_tokens").toString()));
    }

    @Test
    void absentAnswersAreDistinguishableFromAnswers() throws Exception {
        RoutingResult r = RoutingResult.parse(JSON, "fallback");

        assertFalse(r.hasAnswer("nope"));
        assertFalse(r.hasNoul("nope"));
        assertFalse(r.hasNoul("__tool__"), "a choice answer has no noul value");
        assertNull(r.getChoice("nope"));
        assertEquals(0.0, r.getChoiceConfidence("nope"));
        assertEquals(0.5, r.getNoul("nope"), "an absent noul reads as undecided, never as a yes");
    }

    @Test
    void usesTheFallbackModelWhenTheResponseHasNone() throws Exception {
        RoutingResult r = RoutingResult.parse("{\"answers\":{}}", "jev-latest");
        assertEquals("jev-latest", r.getModelVersion());
    }

    @Test
    void roundTripsThroughJson() throws Exception {
        RoutingResult original = RoutingResult.parse(JSON, "fallback");
        RoutingResult copy = RoutingResult.parse(original.toJson(), "fallback");

        assertEquals(original.getModelVersion(), copy.getModelVersion());
        assertEquals(original.getChoice("__tool__"), copy.getChoice("__tool__"));
        assertEquals(original.getNoul("create-user.admin"), copy.getNoul("create-user.admin"), 1e-9);
    }

    @Test
    void nullAnswersAndUsageBecomeEmpty() {
        RoutingResult r = new RoutingResult(null, "m", null);
        assertNotNull(r.getAnswers());
        assertNotNull(r.getUsage());
        assertNull(r.getChoice("x"));
    }

    @Test
    void malformedNumbersFallBackToTheAbsentValue() {
        Builder answers = new Builder();
        Builder a = new Builder();
        a.put("confidence", "not-a-number");
        a.put("noul", "also-not");
        answers.put("q", a);
        RoutingResult r = new RoutingResult(answers, "m", null);

        assertEquals(0.0, r.getChoiceConfidence("q"));
        assertEquals(0.5, r.getNoul("q"));
    }
}
