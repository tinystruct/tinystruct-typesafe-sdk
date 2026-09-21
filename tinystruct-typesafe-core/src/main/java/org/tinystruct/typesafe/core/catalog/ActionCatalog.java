package org.tinystruct.typesafe.core.catalog;

import org.tinystruct.ApplicationException;
import org.tinystruct.application.ActionRegistry;
import org.tinystruct.system.annotation.Action.Mode;
import org.tinystruct.system.cli.CommandLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Turns the allowlist into {@link ActionDefinition}s by asking tinystruct for each action's
 * {@link CommandLine}. It never scans anything: the registry already knows every action.
 *
 * <p>The allowlist is the only way an action becomes routable. Built-in commands and path
 * templates are refused even if listed, and an action declared for another mode is skipped.
 */
public final class ActionCatalog {

    private static final Logger LOGGER = Logger.getLogger(ActionCatalog.class.getName());

    /** Built-in tinystruct commands that must never be routed, even if allowlisted by mistake. */
    private static final Set<String> BUILT_IN = Set.of("start", "generate", "version", "import", "help", "settings");

    private final Set<String> allowed;

    public ActionCatalog(Set<String> allowed) {
        this.allowed = Set.copyOf(allowed);
    }

    /** {@code true} if nothing is allowlisted, so nothing is routable. */
    public boolean isEmpty() {
        return allowed.isEmpty();
    }

    /**
     * @param mode the caller's mode; actions declared for a different specific mode are skipped
     * @return definitions in alphabetical order (so request bodies and cache keys are stable);
     *         allowlisted names that are unknown are logged and skipped
     * @throws ApplicationException if an allowlisted action has a parameter that cannot be described
     */
    public List<ActionDefinition> resolve(ActionRegistry registry, Mode mode) throws ApplicationException {
        List<ActionDefinition> definitions = new ArrayList<>();
        for (String path : orderedAllowlist()) {
            if (BUILT_IN.contains(path) || path.startsWith("--") || path.contains("{")) {
                LOGGER.warning("Ignoring '" + path + "' from the allowlist: built-ins and path templates are not routable.");
                continue;
            }
            CommandLine command = registry.getCommand(path, mode);
            if (command == null) {
                LOGGER.warning("Allowlisted action '" + path + "' is not registered.");
                continue;
            }
            if (!CallerMode.allows(command.getMode(), mode)) {
                LOGGER.fine("Skipping '" + path + "': declared for mode " + command.getMode() + ".");
                continue;
            }
            definitions.add(ActionDefinition.from(command));
        }
        return definitions;
    }

    private List<String> orderedAllowlist() {
        List<String> sorted = new ArrayList<>(allowed);
        java.util.Collections.sort(sorted); // deterministic option order keeps request bodies (and cache keys) stable
        return sorted;
    }
}
