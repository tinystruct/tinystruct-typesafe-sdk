package org.tinystruct.typesafe.core.cache;

import org.tinystruct.typesafe.client.RoutingResult;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Bounded in-memory {@link RoutingCache}: least-recently-used eviction plus a time-to-live. */
public final class MemoryRoutingCache implements RoutingCache {

    public static final int DEFAULT_MAX_ENTRIES = 1024;

    private record Entry(RoutingResult result, long storedAtMs) {}

    private final long ttlMs;
    private final LongSupplier clock;
    private final Map<String, Entry> entries;

    /**
     * @param ttlSeconds time an entry stays valid; zero or negative means no expiry
     * @param maxEntries capacity before the least recently used entry is evicted
     * @param clock      millisecond time source (injectable so expiry can be tested)
     */
    public MemoryRoutingCache(long ttlSeconds, int maxEntries, LongSupplier clock) {
        this.ttlMs = ttlSeconds > 0 ? ttlSeconds * 1000 : 0;
        this.clock = clock;
        final int capacity = Math.max(1, maxEntries);
        this.entries = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > capacity;
            }
        };
    }

    public MemoryRoutingCache(long ttlSeconds) {
        this(ttlSeconds, DEFAULT_MAX_ENTRIES, System::currentTimeMillis);
    }

    public MemoryRoutingCache() {
        this(0);
    }

    @Override
    public synchronized RoutingResult get(String key) {
        Entry entry = entries.get(key);
        if (entry == null) return null;
        if (ttlMs > 0 && clock.getAsLong() - entry.storedAtMs() >= ttlMs) {
            entries.remove(key);
            return null;
        }
        return entry.result();
    }

    @Override
    public synchronized void put(String key, RoutingResult result) {
        entries.put(key, new Entry(result, clock.getAsLong()));
    }

    @Override
    public synchronized void invalidate(String key) {
        entries.remove(key);
    }
}
