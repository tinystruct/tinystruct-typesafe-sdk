package org.tinystruct.typesafe.core.cache;

import org.junit.jupiter.api.Test;
import org.tinystruct.ApplicationException;
import org.tinystruct.data.component.Builder;
import org.tinystruct.typesafe.client.RoutingRequest;
import org.tinystruct.typesafe.client.RoutingResult;
import org.tinystruct.typesafe.client.TypesafeClient;
import org.tinystruct.typesafe.client.testing.MockTypesafeClient;
import org.tinystruct.typesafe.core.metrics.DispatchMetrics;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** The in-memory cache and the caching client decorator. */
class CacheTest {

    private static RoutingResult result(String choice) {
        Builder answers = new Builder();
        Builder a = new Builder();
        a.put("choice", choice);
        a.put("confidence", 0.9);
        answers.put("__tool__", a);
        return new RoutingResult(answers, "jev-1.13.0", new Builder());
    }

    private static RoutingRequest request(String input) {
        return new RoutingRequest(input, new Builder(), "jev-latest");
    }

    // ---- MemoryRoutingCache -----------------------------------------------------------------

    @Test
    void entriesExpireAfterTheTimeToLive() {
        AtomicLong now = new AtomicLong(0);
        MemoryRoutingCache cache = new MemoryRoutingCache(10, 100, now::get);
        cache.put("k", result("a"));

        now.set(9_999);
        assertNotNull(cache.get("k"));
        now.set(10_000);
        assertNull(cache.get("k"), "expired at exactly the TTL");
    }

    @Test
    void zeroTtlMeansNeverExpire() {
        AtomicLong now = new AtomicLong(0);
        MemoryRoutingCache cache = new MemoryRoutingCache(0, 100, now::get);
        cache.put("k", result("a"));
        now.set(Long.MAX_VALUE / 2);
        assertNotNull(cache.get("k"));
    }

    @Test
    void leastRecentlyUsedEntryIsEvictedAtCapacity() {
        MemoryRoutingCache cache = new MemoryRoutingCache(0, 2, System::currentTimeMillis);
        cache.put("a", result("a"));
        cache.put("b", result("b"));
        cache.get("a");               // a is now more recent than b
        cache.put("c", result("c"));

        assertNotNull(cache.get("a"));
        assertNull(cache.get("b"));
        assertNotNull(cache.get("c"));
    }

    @Test
    void invalidateRemovesAnEntry() {
        MemoryRoutingCache cache = new MemoryRoutingCache();
        cache.put("k", result("a"));
        cache.invalidate("k");
        assertNull(cache.get("k"));
    }

    // ---- CachingTypesafeClient --------------------------------------------------------------

    @Test
    void repeatedIdenticalRequestsAreAnsweredFromTheCache() throws Exception {
        MockTypesafeClient upstream = new MockTypesafeClient();
        upstream.addChoiceAnswer("__tool__", "create-user", 0.9);
        DispatchMetrics metrics = new DispatchMetrics();
        CachingTypesafeClient client = new CachingTypesafeClient(upstream, new MemoryRoutingCache(), metrics);

        client.classify(request("make John an admin"));
        client.classify(request("make John an admin"));
        client.classify(request("make Ann an admin"));

        assertEquals(2, upstream.getCallCount(), "the two different inputs each reach TypeSafe once");
        Builder json = metrics.toJson();
        assertEquals(1L, Long.parseLong(json.get("cacheHits").toString()));
        assertEquals(2L, Long.parseLong(json.get("cacheMisses").toString()));
    }

    @Test
    void theKeyCoversInputQuestionsAndModel() {
        Builder q1 = new Builder();
        q1.put("a", "1");
        Builder q2 = new Builder();
        q2.put("a", "2");

        String base = CachingTypesafeClient.keyFor(new RoutingRequest("x", q1, "m1"));
        assertEquals(base, CachingTypesafeClient.keyFor(new RoutingRequest("x", q1, "m1")));
        assertNotEquals(base, CachingTypesafeClient.keyFor(new RoutingRequest("y", q1, "m1")));
        assertNotEquals(base, CachingTypesafeClient.keyFor(new RoutingRequest("x", q2, "m1")), "a changed catalog must miss");
        assertNotEquals(base, CachingTypesafeClient.keyFor(new RoutingRequest("x", q1, "m2")), "a changed model must miss");
    }

    @Test
    void failuresAreNeverCached() {
        AtomicInteger calls = new AtomicInteger();
        TypesafeClient flaky = req -> {
            if (calls.incrementAndGet() == 1) throw new ApplicationException("boom");
            return result("a");
        };
        CachingTypesafeClient client = new CachingTypesafeClient(flaky, new MemoryRoutingCache(), new DispatchMetrics());

        assertThrows(ApplicationException.class, () -> client.classify(request("x")));
        assertDoesNotThrow(() -> client.classify(request("x")));
        assertEquals(2, calls.get());
    }
}
