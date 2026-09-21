package org.tinystruct.typesafe.core.metrics;

import org.junit.jupiter.api.Test;
import org.tinystruct.ApplicationException;
import org.tinystruct.data.component.Builder;
import org.tinystruct.typesafe.client.RoutingRequest;
import org.tinystruct.typesafe.client.RoutingResult;
import org.tinystruct.typesafe.client.TypesafeClient;
import org.tinystruct.typesafe.core.api.DispatchResult;
import org.tinystruct.typesafe.core.metrics.DispatchMetrics;
import org.tinystruct.typesafe.core.metrics.MeteredTypesafeClient;

import static org.junit.jupiter.api.Assertions.*;

/** The counters and the metering client decorator. */
class MetricsTest {

    private static RoutingRequest request(String input) {
        return new RoutingRequest(input, new Builder(), "jev-latest");
    }

    // ---- MeteredTypesafeClient --------------------------------------------------------------

    @Test
    void meteredClientCountsCallsFailuresAndTokens() throws Exception {
        DispatchMetrics metrics = new DispatchMetrics();
        Builder usage = new Builder();
        usage.put("input_tokens", 100);
        usage.put("output_tokens", 7);
        TypesafeClient ok = req -> new RoutingResult(new Builder(), "m", usage);
        TypesafeClient bad = req -> { throw new ApplicationException("down"); };

        new MeteredTypesafeClient(ok, metrics).classify(request("x"));
        assertThrows(ApplicationException.class, () -> new MeteredTypesafeClient(bad, metrics).classify(request("x")));

        Builder json = metrics.toJson();
        assertEquals(2L, Long.parseLong(json.get("upstreamCalls").toString()));
        assertEquals(1L, Long.parseLong(json.get("upstreamFailures").toString()));
        assertEquals(100L, Long.parseLong(json.get("inputTokens").toString()));
        assertEquals(7L, Long.parseLong(json.get("outputTokens").toString()));
    }

    // ---- DispatchMetrics --------------------------------------------------------------------

    @Test
    void routingOutcomesActionsAndConfidenceAreCounted() {
        DispatchMetrics metrics = new DispatchMetrics();
        metrics.recordRouting(DispatchResult.executed("x"), 10);
        metrics.recordRouting(DispatchResult.needsConfirmation("id"), 30);
        metrics.recordRouting(DispatchResult.rejected("no"), 20);
        metrics.recordAmbiguous(0);
        metrics.recordAction("create-user");
        metrics.recordAction("create-user");
        metrics.recordAction("delete-user");
        for (double c : new double[]{0.2, 0.6, 0.85, 0.95, 0.5, 0.8, 0.9}) metrics.recordConfidence(c);
        metrics.recordConfirmation();
        metrics.recordRejection();
        metrics.recordExpiry();
        metrics.recordConflict();
        metrics.recordWrongPrincipal();

        Builder json = metrics.toJson();
        assertEquals("4", json.get("totalRoutings").toString());
        assertEquals("1", json.get("executed").toString());
        assertEquals("1", json.get("needsConfirmation").toString());
        assertEquals("1", json.get("rejected").toString());
        assertEquals("1", json.get("ambiguous").toString());
        assertEquals("15", json.get("avgLatencyMs").toString());
        assertEquals("1", json.get("wrongPrincipal").toString());

        Builder actions = (Builder) json.get("actions");
        assertEquals("2", actions.get("create-user").toString());
        assertEquals("1", actions.get("delete-user").toString());

        Builder buckets = (Builder) json.get("confidence");
        assertEquals("1", buckets.get("below_0.5").toString());
        assertEquals("2", buckets.get("0.5_to_0.8").toString());
        assertEquals("2", buckets.get("0.8_to_0.9").toString());
        assertEquals("2", buckets.get("0.9_and_above").toString());
    }

    @Test
    void averageLatencyIsZeroBeforeAnyRouting() {
        assertEquals("0", new DispatchMetrics().toJson().get("avgLatencyMs").toString());
    }

    @Test
    void malformedUsageCountersAreIgnored() {
        DispatchMetrics metrics = new DispatchMetrics();
        Builder usage = new Builder();
        usage.put("input_tokens", "many");
        metrics.recordUsage(usage);
        metrics.recordUsage(null);
        assertEquals("0", metrics.toJson().get("inputTokens").toString());
    }
}
