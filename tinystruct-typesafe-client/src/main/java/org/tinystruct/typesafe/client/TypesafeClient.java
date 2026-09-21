package org.tinystruct.typesafe.client;

import org.tinystruct.ApplicationException;
import org.tinystruct.typesafe.client.testing.MockTypesafeClient;

/**
 * Abstraction over the TypeSafe /v1/systemone HTTP endpoint.
 *
 * <p>Implementations must be thread-safe. The only production implementation is
 * {@link HttpTypesafeClient}. Tests use {@link MockTypesafeClient}.
 */
public interface TypesafeClient {

    /**
     * Sends a classification request to the TypeSafe Jev model.
     *
     * @param request the questions and input state
     * @return parsed answers, model version, and usage
     * @throws ApplicationException on 401, 422, unrecoverable 429/529, network error, or parse error
     */
    RoutingResult classify(RoutingRequest request) throws ApplicationException;
}
