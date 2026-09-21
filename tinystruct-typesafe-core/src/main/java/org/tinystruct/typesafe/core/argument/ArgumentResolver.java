package org.tinystruct.typesafe.core.argument;

import org.tinystruct.typesafe.client.RoutingResult;
import org.tinystruct.typesafe.core.candidate.CandidateExtractor;
import org.tinystruct.typesafe.core.candidate.ValueCandidate;
import org.tinystruct.typesafe.core.catalog.ActionDefinition;
import org.tinystruct.typesafe.core.catalog.ParameterDefinition;
import org.tinystruct.typesafe.core.question.QuestionGenerator;

import java.util.*;

/**
 * Resolves the argument values of the selected action from a {@link RoutingResult}.
 *
 * <ul>
 *   <li>{@code ENUM_CHOICE}: the chosen constant, checked for membership.</li>
 *   <li>{@code FLAG}: {@code true} when the noul probability is at least 0.5.</li>
 *   <li>{@code SET_ENUM}: one noul per member; members at or above the set threshold are included.</li>
 *   <li>{@code OPEN_VALUE}: the chosen candidate span, checked to be one of the candidates that were
 *       offered, so a value is always a verbatim span of the user's input.</li>
 * </ul>
 *
 * <p>A missing answer is a protocol violation and fails closed: it is never read as a default.
 * A required parameter answered with the {@code none} option is reported in
 * {@link Resolution#unresolved()} so the caller can reject.
 */
public final class ArgumentResolver {

    /** Outcome of resolving one action's parameters. */
    public record Resolution(Map<String, Object> values, List<String> unresolved, List<String> usedQuestions) {}

    private static final double STATED_THRESHOLD = 0.5;
    private static final double FLAG_THRESHOLD = 0.5;

    private final double setThreshold;
    private final CandidateExtractor extractor;

    public ArgumentResolver(double setThreshold, CandidateExtractor extractor) {
        this.setThreshold = setThreshold;
        this.extractor = extractor;
    }

    public Resolution resolve(ActionDefinition action, RoutingResult result, String input, String actionKey)
            throws InvalidActionException {
        Map<String, Object> values = new LinkedHashMap<>();
        List<String> unresolved = new ArrayList<>();
        List<String> used = new ArrayList<>();

        for (ParameterDefinition param : action.getParameters()) {
            String paramKey = actionKey + "." + QuestionGenerator.escape(param.getName());
            String name = param.getName();

            if (param.getKind() == ParameterDefinition.Kind.FLAG) {
                values.put(name, answerNoul(result, paramKey, used) >= FLAG_THRESHOLD);
                continue;
            }
            if (param.getKind() == ParameterDefinition.Kind.SET_ENUM) {
                values.put(name, resolveSet(param, result, paramKey, used));
                continue;
            }

            if (param.isOptional() && answerNoul(result, paramKey + "?", used) < STATED_THRESHOLD) {
                values.put(name, null);
                continue;
            }
            String chosen = answerChoice(result, paramKey, used);
            if (QuestionGenerator.NONE_KEY.equals(chosen)) {
                values.put(name, null);
                if (!param.isOptional()) unresolved.add(name);
            } else if (param.getKind() == ParameterDefinition.Kind.ENUM_CHOICE) {
                values.put(name, toEnum(param, chosen));
            } else {
                requireOffered(param, chosen, input);
                values.put(name, chosen);
            }
        }
        return new Resolution(values, unresolved, used);
    }

    private Object resolveSet(ParameterDefinition param, RoutingResult result, String paramKey, List<String> used)
            throws InvalidActionException {
        List<Enum<?>> selected = new ArrayList<>();
        for (Enum<?> member : param.getEnumType().getEnumConstants()) {
            if (answerNoul(result, paramKey + "." + member.name(), used) >= setThreshold) {
                selected.add(member);
            }
        }
        if (List.class.isAssignableFrom(param.getRawType())) return selected;
        return new LinkedHashSet<>(selected);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Enum<?> toEnum(ParameterDefinition param, String chosen) throws InvalidActionException {
        try {
            return Enum.valueOf((Class<? extends Enum>) param.getEnumType(), chosen);
        } catch (IllegalArgumentException e) {
            throw new InvalidActionException("Model returned an invalid value for parameter '"
                    + param.getName() + "'.");
        }
    }

    /** The chosen value must be one of the spans that were offered for this input. */
    private void requireOffered(ParameterDefinition param, String chosen, String input) throws InvalidActionException {
        for (ValueCandidate candidate : extractor.extract(input, param)) {
            if (candidate.getValue().equals(chosen)) return;
        }
        throw new InvalidActionException("Model returned a value for parameter '" + param.getName()
                + "' that is not part of the input.");
    }

    private static String answerChoice(RoutingResult result, String key, List<String> used) throws InvalidActionException {
        String choice = result.getChoice(key);
        if (choice == null) {
            throw new InvalidActionException("Model response has no answer for question '" + key + "'.");
        }
        used.add(key);
        return choice;
    }

    private static double answerNoul(RoutingResult result, String key, List<String> used) throws InvalidActionException {
        if (!result.hasNoul(key)) {
            throw new InvalidActionException("Model response has no answer for question '" + key + "'.");
        }
        used.add(key);
        return result.getNoul(key);
    }
}
