package org.tinystruct.typesafe.core.pipeline;

import org.tinystruct.ApplicationException;
import org.tinystruct.application.ActionRegistry;
import org.tinystruct.system.annotation.Action.Mode;
import org.tinystruct.typesafe.client.RoutingResult;
import org.tinystruct.typesafe.core.argument.ArgumentResolver;
import org.tinystruct.typesafe.core.argument.ArgumentValidator;
import org.tinystruct.typesafe.core.argument.InvalidActionException;
import org.tinystruct.typesafe.core.catalog.ActionCatalog;
import org.tinystruct.typesafe.core.catalog.ActionDefinition;
import org.tinystruct.typesafe.core.config.TypesafeConfig;
import org.tinystruct.typesafe.core.policy.ConfidencePolicy;
import org.tinystruct.typesafe.core.policy.ConfidenceScore;
import org.tinystruct.typesafe.core.question.QuestionGenerator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Turns an instruction into a validated call, and nothing more: it talks to the model and checks
 * what came back. It never runs, holds or refuses-by-policy anything; that is the
 * {@link DispatchPipeline}'s decision.
 *
 * <p>Model output is untrusted, so every answer is checked here: the chosen action must be one that
 * was offered, every argument must resolve and validate, and a missing answer is a refusal.
 */
public final class Interpreter {

    private static final Logger LOGGER = Logger.getLogger(Interpreter.class.getName());

    private final ActionCatalog catalog;
    private final Classifier classifier;
    private final ArgumentResolver resolver;
    private final ArgumentValidator validator;
    private final ConfidencePolicy policy;

    public Interpreter(ActionCatalog catalog, Classifier classifier, ArgumentResolver resolver,
                       ArgumentValidator validator, ConfidencePolicy policy) {
        this.catalog = catalog;
        this.classifier = classifier;
        this.resolver = resolver;
        this.validator = validator;
        this.policy = policy;
    }

    public Interpretation interpret(String input, ActionRegistry registry, Mode mode) throws ApplicationException {
        if (catalog.isEmpty()) {
            LOGGER.warning(TypesafeConfig.ALLOWED_ACTIONS + " is empty; no action is routable.");
            return Interpretation.refused("No actions are allowlisted.");
        }
        List<ActionDefinition> actions = catalog.resolve(registry, mode);
        if (actions.isEmpty()) {
            return Interpretation.refused("None of the allowlisted actions is available in this mode.");
        }

        try {
            return interpret(input, actions, classifier.classify(input, actions));
        } catch (InvalidActionException e) {
            // The model's answer did not hold up. That is a refusal, not a failure of the caller.
            return Interpretation.refused(e.getMessage());
        }
    }

    private Interpretation interpret(String input, List<ActionDefinition> actions, RoutingResult result)
            throws InvalidActionException {
        String tool = result.getChoice(QuestionGenerator.TOOL_KEY);
        if (tool == null) {
            throw new InvalidActionException("Model response has no answer for the action selection.");
        }
        if (QuestionGenerator.OTHER_KEY.equals(tool)) {
            return Interpretation.refused("No allowlisted action matches the request.");
        }
        ActionDefinition action = offered(actions).get(tool);
        if (action == null) {
            LOGGER.warning("Model selected an action that is not allowlisted: " + tool);
            return Interpretation.refused("The selected action is not allowlisted.");
        }

        try {
            ArgumentResolver.Resolution resolution =
                    resolver.resolve(action, result, input, QuestionGenerator.escape(action.getActionPath()));
            if (!resolution.unresolved().isEmpty()) {
                return Interpretation.refused("Could not find a value for: "
                        + String.join(", ", resolution.unresolved()) + ".", action);
            }
            validator.validate(action, resolution.values());
            ConfidenceScore score = policy.computeOverall(result, resolution.usedQuestions());
            return Interpretation.matched(action, resolution.values(), score);
        } catch (InvalidActionException e) {
            return Interpretation.refused(e.getMessage(), action);
        }
    }

    /** The offered actions keyed the way they appear in the question. */
    private static Map<String, ActionDefinition> offered(List<ActionDefinition> actions) {
        return actions.stream().collect(Collectors.toMap(
                a -> QuestionGenerator.escape(a.getActionPath()), a -> a, (a, b) -> a, LinkedHashMap::new));
    }
}
