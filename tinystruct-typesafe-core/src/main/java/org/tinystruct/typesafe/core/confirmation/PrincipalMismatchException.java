package org.tinystruct.typesafe.core.confirmation;

import org.tinystruct.ApplicationException;

/** Thrown when confirm or reject is attempted by someone other than the originator of a pending call. */
public class PrincipalMismatchException extends ApplicationException {

    public PrincipalMismatchException(String pendingId) {
        super("Pending call " + pendingId + " belongs to a different caller.");
    }
}
