package org.tinystruct.typesafe.core.metrics;

import org.tinystruct.ApplicationException;
import org.tinystruct.typesafe.client.RoutingRequest;
import org.tinystruct.typesafe.client.RoutingResult;
import org.tinystruct.typesafe.client.TypesafeClient;

/** Decorator that counts upstream calls, failures and token usage. Wrap the real client with it, inside any cache. */
public final class MeteredTypesafeClient implements TypesafeClient {

    private final TypesafeClient delegate;
    private final DispatchMetrics metrics;

    public MeteredTypesafeClient(TypesafeClient delegate, DispatchMetrics metrics) {
        this.delegate = delegate;
        this.metrics = metrics;
    }

    @Override
    public RoutingResult classify(RoutingRequest request) throws ApplicationException {
        try {
            RoutingResult result = delegate.classify(request);
            metrics.recordUpstream(true);
            metrics.recordUsage(result.getUsage());
            return result;
        } catch (ApplicationException | RuntimeException e) {
            metrics.recordUpstream(false);
            throw e;
        }
    }
}
