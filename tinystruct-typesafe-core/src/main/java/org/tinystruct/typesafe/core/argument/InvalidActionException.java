package org.tinystruct.typesafe.core.argument;

import org.tinystruct.ApplicationException;

/** Thrown when the model selects an action that is not on the allowlist or has invalid arguments. */
public class InvalidActionException extends ApplicationException {
    public InvalidActionException(String message) {
        super(message);
    }
}
