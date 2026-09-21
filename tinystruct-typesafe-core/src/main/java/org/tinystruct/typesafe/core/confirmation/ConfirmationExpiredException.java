package org.tinystruct.typesafe.core.confirmation;

import org.tinystruct.ApplicationException;

/** Thrown when a pending call is confirmed or rejected after its expiry time. */
public class ConfirmationExpiredException extends ApplicationException {
    public ConfirmationExpiredException(String pendingId) {
        super("Pending call " + pendingId + " has expired and was cancelled.");
    }
}
