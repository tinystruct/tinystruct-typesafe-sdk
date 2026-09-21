package org.tinystruct.typesafe.core.metrics;

import org.tinystruct.data.component.Builder;
import org.tinystruct.typesafe.core.api.DispatchResult;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process counters, exposed as JSON by {@code typesafe/metrics}. Thread-safe.
 *
 * <p>Deliberately plain: tinystruct has no metrics facility, and pulling in a metrics library would
 * break its zero-dependency stance. Anything richer can read {@link #toJson()}.
 */
public final class DispatchMetrics {

    private final AtomicLong totalRoutings = new AtomicLong();
    private final AtomicLong executed = new AtomicLong();
    private final AtomicLong needsConfirmation = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong ambiguous = new AtomicLong();
    private final AtomicLong confirmed = new AtomicLong();
    private final AtomicLong userRejections = new AtomicLong();
    private final AtomicLong expired = new AtomicLong();
    private final AtomicLong conflicts = new AtomicLong();
    private final AtomicLong wrongPrincipal = new AtomicLong();
    private final AtomicLong totalLatencyMs = new AtomicLong();
    private final AtomicLong upstreamCalls = new AtomicLong();
    private final AtomicLong upstreamFailures = new AtomicLong();
    private final AtomicLong cacheHits = new AtomicLong();
    private final AtomicLong cacheMisses = new AtomicLong();
    private final AtomicLong inputTokens = new AtomicLong();
    private final AtomicLong outputTokens = new AtomicLong();
    private final AtomicLong[] confidenceBuckets = {
            new AtomicLong(), new AtomicLong(), new AtomicLong(), new AtomicLong()};
    private final Map<String, AtomicLong> actionHits = new ConcurrentHashMap<>();

    private static final String[] BUCKET_NAMES = {"below_0.5", "0.5_to_0.8", "0.8_to_0.9", "0.9_and_above"};

    public void recordRouting(DispatchResult result, long latencyMs) {
        totalRoutings.incrementAndGet();
        totalLatencyMs.addAndGet(latencyMs);
        switch (result.getStatus()) {
            case EXECUTED -> executed.incrementAndGet();
            case NEEDS_CONFIRMATION -> needsConfirmation.incrementAndGet();
            case REJECTED -> rejected.incrementAndGet();
        }
    }

    /** A routing that ended in {@code AmbiguousIntentException}. */
    public void recordAmbiguous(long latencyMs) {
        totalRoutings.incrementAndGet();
        totalLatencyMs.addAndGet(latencyMs);
        ambiguous.incrementAndGet();
    }

    public void recordConfidence(double confidence) {
        int bucket = confidence < 0.5 ? 0 : confidence < 0.8 ? 1 : confidence < 0.9 ? 2 : 3;
        confidenceBuckets[bucket].incrementAndGet();
    }

    /** The action the model selected (whether or not it then ran). */
    public void recordAction(String actionPath) {
        actionHits.computeIfAbsent(actionPath, k -> new AtomicLong()).incrementAndGet();
    }

    public void recordConfirmation() { confirmed.incrementAndGet(); }
    public void recordRejection() { userRejections.incrementAndGet(); }
    public void recordExpiry() { expired.incrementAndGet(); }
    public void recordConflict() { conflicts.incrementAndGet(); }
    public void recordWrongPrincipal() { wrongPrincipal.incrementAndGet(); }

    public void recordCache(boolean hit) {
        (hit ? cacheHits : cacheMisses).incrementAndGet();
    }

    public void recordUpstream(boolean success) {
        upstreamCalls.incrementAndGet();
        if (!success) upstreamFailures.incrementAndGet();
    }

    public void recordUsage(Builder usage) {
        if (usage == null) return;
        inputTokens.addAndGet(count(usage, "input_tokens"));
        outputTokens.addAndGet(count(usage, "output_tokens"));
    }

    private static long count(Builder usage, String key) {
        Object value = usage.get(key);
        if (value == null) return 0;
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public Builder toJson() {
        Builder b = new Builder();
        b.put("totalRoutings", totalRoutings.get());
        b.put("executed", executed.get());
        b.put("needsConfirmation", needsConfirmation.get());
        b.put("rejected", rejected.get());
        b.put("ambiguous", ambiguous.get());
        b.put("confirmed", confirmed.get());
        b.put("userRejections", userRejections.get());
        b.put("expired", expired.get());
        b.put("conflicts", conflicts.get());
        b.put("wrongPrincipal", wrongPrincipal.get());
        long count = totalRoutings.get();
        b.put("avgLatencyMs", count > 0 ? totalLatencyMs.get() / count : 0);
        b.put("upstreamCalls", upstreamCalls.get());
        b.put("upstreamFailures", upstreamFailures.get());
        b.put("cacheHits", cacheHits.get());
        b.put("cacheMisses", cacheMisses.get());
        b.put("inputTokens", inputTokens.get());
        b.put("outputTokens", outputTokens.get());

        Builder confidence = new Builder();
        for (int i = 0; i < BUCKET_NAMES.length; i++) {
            confidence.put(BUCKET_NAMES[i], confidenceBuckets[i].get());
        }
        b.put("confidence", confidence);

        Builder actions = new Builder();
        new TreeMap<>(actionHits).forEach((path, hits) -> actions.put(path, hits.get()));
        b.put("actions", actions);
        return b;
    }
}
