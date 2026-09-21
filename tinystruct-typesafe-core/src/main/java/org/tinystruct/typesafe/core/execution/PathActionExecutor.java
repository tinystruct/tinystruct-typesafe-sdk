package org.tinystruct.typesafe.core.execution;

import org.tinystruct.ApplicationException;
import org.tinystruct.application.Action;
import org.tinystruct.application.ActionRegistry;
import org.tinystruct.application.Context;
import org.tinystruct.system.ApplicationManager;
import org.tinystruct.system.annotation.Action.Mode;
import org.tinystruct.typesafe.core.argument.ArgumentValidator;
import org.tinystruct.typesafe.core.argument.InvalidActionException;
import org.tinystruct.typesafe.core.catalog.ActionDefinition;
import org.tinystruct.typesafe.core.catalog.ParameterDefinition;

import java.util.Collection;
import java.util.Map;

/**
 * {@link ActionExecutor} that hands a call to tinystruct exactly as a user would type it:
 * {@code create-user/John/ADMIN}, dispatched by {@link ApplicationManager#call}. Mode checks, context
 * binding and argument conversion are all the framework's.
 *
 * <p>The only logic here is turning typed arguments into path segments, and it stays safe because
 * {@link ArgumentValidator} has already refused segment separators, and the resolved action is
 * checked to be the allowlisted one before it runs.
 *
 * <p>Path binding is positional, so a parameter can only be left out at the end. That is how
 * tinystruct expresses optional parameters (an overload with fewer parameters).
 */
public final class PathActionExecutor implements ActionExecutor {

    private final ActionRegistry registry;

    public PathActionExecutor(ActionRegistry registry) {
        this.registry = registry;
    }

    public PathActionExecutor() {
        this(ActionRegistry.getInstance());
    }

    @Override
    public Object execute(ActionDefinition action, Map<String, Object> arguments, Context context, Mode mode)
            throws ApplicationException {
        String path = pathFor(action, arguments);
        requireAllowlistedAction(action, registry.getAction(path, mode), path);
        return ApplicationManager.call(path, context, mode);
    }

    private static int segmentCount(String path) {
        return path.split("/").length - 1;
    }

    /**
     * The request path for a call: the action name followed by one segment per supplied argument.
     *
     * @throws InvalidActionException if an optional parameter is skipped but a later one is supplied
     */
    static String pathFor(ActionDefinition action, Map<String, Object> arguments) throws InvalidActionException {
        StringBuilder path = new StringBuilder(action.getActionPath());
        boolean skipped = false;
        for (ParameterDefinition param : action.getParameters()) {
            String segment = segment(arguments.get(param.getName()));
            if (segment == null) {
                skipped = true;
            } else if (skipped) {
                throw new InvalidActionException("Parameter '" + param.getName() + "' is given but an earlier "
                        + "optional parameter is not; tinystruct binds arguments by position.");
            } else {
                path.append('/').append(segment);
            }
        }
        return path.toString();
    }

    private static String segment(Object value) {
        if (value == null) return null;
        if (value instanceof Enum<?> constant) return constant.name();
        if (value instanceof Collection<?> members) {
            if (members.isEmpty()) return null;
            StringBuilder csv = new StringBuilder();
            for (Object member : members) {
                if (csv.length() > 0) csv.append(',');
                csv.append(member instanceof Enum<?> e ? e.name() : member);
            }
            return csv.toString();
        }
        return value.toString();
    }

    /**
     * The registry resolves a path by pattern, so confirm it landed on this action and not on some
     * other action whose pattern happens to match: same declared name, same application, and exactly
     * one capture group per supplied argument.
     */
    private static void requireAllowlistedAction(ActionDefinition expected, Action resolved, String path)
            throws ApplicationException {
        if (resolved == null || !isTheAllowlistedAction(expected, resolved, path)) {
            throw new ApplicationException("No matching function found for path [" + path + "]. "
                    + "Check that the action accepts this many arguments.", 404);
        }
    }

    private static boolean isTheAllowlistedAction(ActionDefinition expected, Action resolved, String path) {
        String rule = resolved.getPathRule();
        String prefix = "^/?" + expected.getActionPath();
        boolean sameName = rule.equals(prefix + "$") || rule.startsWith(prefix + "/");
        boolean sameApplication = expected.getApplicationName() == null
                || expected.getApplicationName().equals(resolved.getApplicationName());
        int groups = resolved.getPattern().matcher("").groupCount();
        int suppliedSegments = segmentCount(path) - segmentCount(expected.getActionPath());
        return sameName && sameApplication && groups == suppliedSegments;
    }
}
