package org.tinystruct.typesafe.core.question;

import org.tinystruct.data.component.Builder;
import org.tinystruct.typesafe.core.candidate.CandidateExtractor;
import org.tinystruct.typesafe.core.candidate.ValueCandidate;
import org.tinystruct.typesafe.core.catalog.ActionDefinition;
import org.tinystruct.typesafe.core.catalog.ParameterDefinition;

import java.util.List;

/**
 * Converts {@link ActionDefinition}s into the {@code questions} map of a TypeSafe
 * {@code /v1/systemone} request.
 *
 * <p>Every question follows TypeSafe's documented shape:
 * <ul>
 *   <li>{@code choice}: {@code {"type":"choice","instructions":"...","criteria":{"option":"description"}}}</li>
 *   <li>{@code noul}: {@code {"type":"noul","instructions":"..."}}</li>
 * </ul>
 *
 * <p>Question keys (dots and question marks in action and parameter names are escaped):
 * <ul>
 *   <li>{@code __tool__}: choice over the allowlisted actions plus {@link #OTHER_KEY}</li>
 *   <li>{@code <action>.<param>}: choice over enum constants, or over input candidates plus {@link #NONE_KEY}</li>
 *   <li>{@code <action>.<param>?}: noul, "was it stated"</li>
 *   <li>{@code <action>.<param>.<MEMBER>}: noul per set member</li>
 * </ul>
 *
 * <p>Jev never generates values, so free-text arguments are offered as a closed set of verbatim
 * spans of the input (see {@link CandidateExtractor}).
 */
public final class QuestionGenerator {

    /** Key of the action-selection question. */
    public static final String TOOL_KEY = "__tool__";
    /** Option meaning "none of the actions apply". Reserved: never an action name. */
    public static final String OTHER_KEY = "__other__";
    /** Option meaning "not stated in the input". Reserved: never an argument value. */
    public static final String NONE_KEY = "__none__";

    /** Max options per TypeSafe choice question. */
    public static final int MAX_CHOICE_OPTIONS = 255;

    private QuestionGenerator() {}

    /** Builds the complete question map for the single-stage strategy. */
    public static Builder buildAllQuestions(List<ActionDefinition> actions,
                                            CandidateExtractor extractor,
                                            String input) {
        Builder questions = new Builder();
        questions.put(TOOL_KEY, buildToolQuestion(actions));
        for (ActionDefinition action : actions) {
            buildArgumentQuestions(action, extractor, input, questions);
        }
        return questions;
    }

    /** Builds only the {@code __tool__} question (first stage of the two-stage strategy). */
    public static Builder buildToolQuestion(List<ActionDefinition> actions) {
        Builder criteria = new Builder();
        for (ActionDefinition a : actions) {
            criteria.put(escape(a.getActionPath()), a.getDescription());
        }
        criteria.put(OTHER_KEY, "None of the actions above matches what the user asked for.");
        return choice("Which action does the user's message ask for? "
                + "Choose " + OTHER_KEY + " if none of the actions applies.", criteria);
    }

    /** Builds the argument questions for one action into {@code target}. */
    public static void buildArgumentQuestions(ActionDefinition action,
                                              CandidateExtractor extractor,
                                              String input,
                                              Builder target) {
        String actionKey = escape(action.getActionPath());
        for (ParameterDefinition param : action.getParameters()) {
            String paramKey = actionKey + "." + escape(param.getName());
            String about = "the action '" + action.getActionPath() + "' parameter '" + param.getName() + "'"
                    + (param.getDescription().isBlank() ? "" : " (" + param.getDescription() + ")");

            switch (param.getKind()) {
                case ENUM_CHOICE -> {
                    addStatedQuestion(target, paramKey, param, about);
                    Builder criteria = new Builder();
                    for (Enum<?> constant : param.getEnumType().getEnumConstants()) {
                        criteria.put(constant.name(), (String) null);
                    }
                    criteria.put(NONE_KEY, "The user did not say.");
                    target.put(paramKey, choice("Which value does the user give for " + about + "?", criteria));
                }
                case FLAG -> target.put(paramKey,
                        noul("Does the user's message say yes to " + about + "?"));
                case SET_ENUM -> {
                    for (Enum<?> member : param.getEnumType().getEnumConstants()) {
                        target.put(paramKey + "." + member.name(), noul(
                                "Does the user's message explicitly include " + member.name() + " for " + about + "?"));
                    }
                }
                case OPEN_VALUE -> {
                    addStatedQuestion(target, paramKey, param, about);
                    Builder criteria = new Builder();
                    int count = 0;
                    for (ValueCandidate candidate : extractor.extract(input, param)) {
                        if (count >= MAX_CHOICE_OPTIONS - 1) break; // reserve one slot for NONE_KEY
                        if (isReserved(candidate.getValue())) continue;
                        criteria.put(candidate.getValue(), (String) null);
                        count++;
                    }
                    criteria.put(NONE_KEY, "The user did not say.");
                    target.put(paramKey, choice("Which exact text from the user's message is the value for "
                            + about + "? Choose " + NONE_KEY + " if it is not stated.", criteria));
                }
            }
        }
    }

    private static void addStatedQuestion(Builder target, String paramKey, ParameterDefinition param, String about) {
        if (param.isOptional()) {
            target.put(paramKey + "?", noul("Does the user's message explicitly state " + about + "?"));
        }
    }

    private static Builder choice(String instructions, Builder criteria) {
        Builder q = new Builder();
        q.put("type", "choice");
        q.put("instructions", instructions);
        q.put("criteria", criteria);
        return q;
    }

    private static Builder noul(String instructions) {
        Builder q = new Builder();
        q.put("type", "noul");
        q.put("instructions", instructions);
        return q;
    }

    private static boolean isReserved(String value) {
        return NONE_KEY.equals(value) || OTHER_KEY.equals(value);
    }

    /** Escapes dots and question marks in action/parameter names used as question keys. */
    public static String escape(String name) {
        return name.replace(".", "__dot__").replace("?", "__q__");
    }
}
