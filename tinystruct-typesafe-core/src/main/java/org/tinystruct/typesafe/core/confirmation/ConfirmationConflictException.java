package org.tinystruct.typesafe.core.confirmation;

import org.tinystruct.ApplicationException;

/**
 * Thrown when two concurrent confirm calls race, and the losing thread finds
 * the execution is no longer WAITING.
 */
public class ConfirmationConflictException extends ApplicationException {
    public ConfirmationConflictException(String pendingId) {
        super("Concurrent confirm conflict on pending call " + pendingId +
              ": the call was already confirmed or cancelled.");
    }
}
