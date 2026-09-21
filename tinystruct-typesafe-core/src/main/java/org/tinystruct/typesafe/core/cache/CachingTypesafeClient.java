package org.tinystruct.typesafe.core.cache;

import org.tinystruct.ApplicationException;
import org.tinystruct.typesafe.client.RoutingRequest;
import org.tinystruct.typesafe.client.RoutingResult;
import org.tinystruct.typesafe.client.TypesafeClient;
import org.tinystruct.typesafe.core.metrics.DispatchMetrics;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Decorator that answers repeated requests from a {@link RoutingCache}.
 *
 * <p>Jev is deterministic for a given request, so the whole request body (input, question catalog
 * and model) is the cache key: changing the allowlist, an action's description or the model
 * changes the key, and the old entry is simply never hit again. Failures are never cached.
 */
public final class CachingTypesafeClient implements TypesafeClient {

    private final TypesafeClient delegate;
    private final RoutingCache cache;
    private final DispatchMetrics metrics;

    public CachingTypesafeClient(TypesafeClient delegate, RoutingCache cache, DispatchMetrics metrics) {
        this.delegate = delegate;
        this.cache = cache;
        this.metrics = metrics;
    }

    @Override
    public RoutingResult classify(RoutingRequest request) throws ApplicationException {
        String key = keyFor(request);
        RoutingResult cached = cache.get(key);
        if (cached != null) {
            metrics.recordCache(true);
            return cached;
        }
        metrics.recordCache(false);
        RoutingResult fresh = delegate.classify(request);
        cache.put(key, fresh);
        return fresh;
    }

    static String keyFor(RoutingRequest request) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(request.toJson().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }
}
