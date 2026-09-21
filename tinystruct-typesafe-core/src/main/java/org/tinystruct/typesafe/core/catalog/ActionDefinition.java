package org.tinystruct.typesafe.core.catalog;

import org.tinystruct.ApplicationException;
import org.tinystruct.system.annotation.Action.Mode;
import org.tinystruct.system.cli.CommandArgument;
import org.tinystruct.system.cli.CommandLine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A routable action as TypeSafe sees it: its declared path, description and ordered parameters.
 *
 * <p>It is a plain value built from tinystruct's own {@link CommandLine} for the action, which the
 * framework fills from {@code @Action}. It carries no reference to methods, so invoking it goes
 * through tinystruct (see {@code ActionExecutor}).
 */
public final class ActionDefinition {

    private final String actionPath;
    private final String description;
    private final List<ParameterDefinition> parameters;
    private final Mode mode;
    private final String applicationName;

    public ActionDefinition(String actionPath, String description, List<ParameterDefinition> parameters,
                            Mode mode, String applicationName) {
        this.actionPath = actionPath;
        this.description = description == null || description.isBlank() ? actionPath : description;
        this.parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
        this.mode = mode == null ? Mode.DEFAULT : mode;
        this.applicationName = applicationName;
    }

    /** Definition that is not tied to a particular application. */
    public ActionDefinition(String actionPath, String description, List<ParameterDefinition> parameters, Mode mode) {
        this(actionPath, description, parameters, mode, null);
    }

    /** Builds a definition from the framework's command metadata. */
    public static ActionDefinition from(CommandLine command) throws ApplicationException {
        List<ParameterDefinition> params = new ArrayList<>();
        for (CommandArgument<String, Object> argument : command.getArguments()) {
            params.add(ParameterDefinition.from(command.getCommand(), argument));
        }
        String application = command.getApplication() == null ? null : command.getApplication().getName();
        return new ActionDefinition(command.getCommand(), command.getDescription(), params, command.getMode(), application);
    }

    /** The value declared in {@code @Action(value = ...)}. */
    public String getActionPath() { return actionPath; }

    public String getDescription() { return description; }

    /** Parameters in positional order. */
    public List<ParameterDefinition> getParameters() { return parameters; }

    /** The {@code @Action.mode} the action was declared with. */
    public Mode getMode() { return mode; }

    /** Name of the application that declares the action, or {@code null} if unknown. */
    public String getApplicationName() { return applicationName; }

    @Override
    public String toString() {
        return "ActionDefinition{path=" + actionPath + ", params=" + parameters.size() + "}";
    }
}
