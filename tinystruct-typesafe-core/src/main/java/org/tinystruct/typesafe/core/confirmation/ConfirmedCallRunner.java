package org.tinystruct.typesafe.core.confirmation;

import org.tinystruct.ApplicationException;
import org.tinystruct.application.ActionRegistry;
import org.tinystruct.application.Context;
import org.tinystruct.data.component.Builder;
import org.tinystruct.system.annotation.Action.Mode;
import org.tinystruct.typesafe.core.argument.ArgumentCodec;
import org.tinystruct.typesafe.core.argument.ArgumentValidator;
import org.tinystruct.typesafe.core.catalog.ActionCatalog;
import org.tinystruct.typesafe.core.catalog.ActionDefinition;
import org.tinystruct.typesafe.core.execution.ActionExecutor;

import java.util.Map;

/**
 * Runs a call that was held for confirmation, after re-checking everything that may have changed
 * while it waited: the action must still be allowlisted and available in the mode it was opened
 * in, the stored arguments must still fit the action's signature, and they are validated again.
 */
public final class ConfirmedCallRunner {

    private final ActionCatalog catalog;
    private final ArgumentValidator validator;
    private final ActionExecutor executor;

    public ConfirmedCallRunner(ActionCatalog catalog, ArgumentValidator validator, ActionExecutor executor) {
        this.catalog = catalog;
        this.validator = validator;
        this.executor = executor;
    }

    /**
     * @param modeName         the {@code Mode} name recorded when the call was opened
     * @param encodedArguments the arguments as stored by {@link ArgumentCodec#encode}
     * @return whatever the action returns
     * @throws ApplicationException if the action is no longer routable or the arguments no longer fit
     */
    public Object run(ActionRegistry registry, Context context, String actionPath, String modeName,
                      Builder encodedArguments) throws ApplicationException {
        Mode mode = parse(modeName);
        ActionDefinition action = catalog.resolve(registry, mode).stream()
                .filter(a -> a.getActionPath().equals(actionPath))
                .findFirst()
                .orElseThrow(() -> new ApplicationException("Action '" + actionPath
                        + "' is no longer allowlisted or available."));

        Map<String, Object> arguments = ArgumentCodec.decode(action, encodedArguments);
        validator.validate(action, arguments);
        return executor.execute(action, arguments, context, mode);
    }

    private static Mode parse(String name) {
        try {
            return Mode.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return Mode.CLI;
        }
    }
}
