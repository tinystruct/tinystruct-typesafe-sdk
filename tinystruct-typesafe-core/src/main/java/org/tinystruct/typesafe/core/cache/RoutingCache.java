package org.tinystruct.typesafe.core.cache;

import org.tinystruct.typesafe.client.RoutingResult;

/**
 * Cache of classification results. Only what TypeSafe answered is cached, never the outcome of an
 * executed action. Implementations must be thread-safe.
 */
public interface RoutingCache {

    /** @return the cached result, or {@code null} if absent or expired */
    RoutingResult get(String key);

    void put(String key, RoutingResult result);

    void invalidate(String key);
}
