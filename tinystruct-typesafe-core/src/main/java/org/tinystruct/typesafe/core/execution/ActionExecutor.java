package org.tinystruct.typesafe.core.execution;

import org.tinystruct.ApplicationException;
import org.tinystruct.application.Context;
import org.tinystruct.system.annotation.Action.Mode;
import org.tinystruct.typesafe.core.catalog.ActionDefinition;

import java.util.Map;

/**
 * Runs a validated call. The pipeline depends on this abstraction rather than on how tinystruct is
 * invoked, so it can be tested without a registry.
 */
public interface ActionExecutor {

    /**
     * @param action    the allowlisted action
     * @param arguments validated arguments, keyed by parameter name
     * @param context   the caller's context, or {@code null}
     * @param mode      the caller's mode
     * @return whatever the action returns
     */
    Object execute(ActionDefinition action, Map<String, Object> arguments, Context context, Mode mode)
            throws ApplicationException;
}
