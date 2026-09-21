package org.tinystruct.typesafe.core.api;

import org.tinystruct.ApplicationException;
import org.tinystruct.application.ActionRegistry;
import org.tinystruct.application.Context;
import org.tinystruct.typesafe.core.policy.AmbiguousIntentException;

/** Routes a natural-language instruction to an allowlisted {@code @Action}. */
public interface ActionSemanticRouter {

    /**
     * @param context the caller's context (mode and principal are read from it), or {@code null}
     * @throws AmbiguousIntentException if the model's confidence is below the configured minimum
     */
    DispatchResult route(String input, ActionRegistry registry, Context context) throws ApplicationException;

    /** Routes without a caller context: CLI mode and the default principal. */
    default DispatchResult route(String input, ActionRegistry registry) throws ApplicationException {
        return route(input, registry, null);
    }
}
