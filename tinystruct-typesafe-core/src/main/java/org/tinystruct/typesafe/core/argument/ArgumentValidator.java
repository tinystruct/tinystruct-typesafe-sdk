package org.tinystruct.typesafe.core.argument;

import org.tinystruct.typesafe.core.catalog.ActionDefinition;
import org.tinystruct.typesafe.core.catalog.ParameterDefinition;

import java.util.Collection;
import java.util.Map;

/**
 * Validates resolved arguments before anything is executed, and again at confirmation time.
 *
 * <p>Model output is untrusted (Jev does not resist injected instructions), so this checks
 * every value independently of how it was produced: presence of required values, enum membership,
 * value types, string length, control characters and numeric syntax.
 */
public final class ArgumentValidator {

    /** Default maximum length of a string argument. */
    public static final int DEFAULT_MAX_LENGTH = 200;

    private final int maxLength;

    public ArgumentValidator(int maxLength) {
        this.maxLength = maxLength > 0 ? maxLength : DEFAULT_MAX_LENGTH;
    }

    public ArgumentValidator() {
        this(DEFAULT_MAX_LENGTH);
    }

    /**
     * @throws InvalidActionException on the first violation. The message names the parameter but
     *                                never echoes the offending value.
     */
    public void validate(ActionDefinition action, Map<String, Object> arguments) throws InvalidActionException {
        for (String key : arguments.keySet()) {
            if (action.getParameters().stream().noneMatch(p -> p.getName().equals(key))) {
                throw new InvalidActionException("Unexpected argument '" + key + "' for action '"
                        + action.getActionPath() + "'.");
            }
        }
        for (ParameterDefinition param : action.getParameters()) {
            validateParameter(param, arguments.get(param.getName()));
        }
    }

    private void validateParameter(ParameterDefinition param, Object value) throws InvalidActionException {
        String name = param.getName();
        switch (param.getKind()) {
            case ENUM_CHOICE -> {
                if (value == null) requireOptional(param);
                else if (!param.getEnumType().isInstance(value)) {
                    throw new InvalidActionException("Parameter '" + name + "' is not a valid "
                            + param.getEnumType().getSimpleName() + ".");
                }
            }
            case FLAG -> {
                if (value == null) requireOptional(param);
                else if (!(value instanceof Boolean)) {
                    throw new InvalidActionException("Parameter '" + name + "' must be a boolean.");
                }
            }
            case SET_ENUM -> {
                if (value == null) {
                    requireOptional(param);
                } else if (!(value instanceof Collection<?> members)) {
                    throw new InvalidActionException("Parameter '" + name + "' must be a collection.");
                } else {
                    if (members.isEmpty() && !param.isOptional()) {
                        throw new InvalidActionException("Parameter '" + name + "' needs at least one value.");
                    }
                    for (Object member : members) {
                        if (!param.getEnumType().isInstance(member)) {
                            throw new InvalidActionException("Parameter '" + name
                                    + "' contains a value that is not a valid "
                                    + param.getEnumType().getSimpleName() + ".");
                        }
                    }
                }
            }
            case OPEN_VALUE -> {
                if (value == null) {
                    requireOptional(param);
                } else if (!(value instanceof String text)) {
                    throw new InvalidActionException("Parameter '" + name + "' must be text.");
                } else {
                    validateText(name, text);
                    validateNumeric(name, param.getRawType(), text);
                }
            }
        }
    }

    private static void requireOptional(ParameterDefinition param) throws InvalidActionException {
        if (!param.isOptional()) {
            throw new InvalidActionException("Required parameter '" + param.getName() + "' has no value.");
        }
    }

    private void validateText(String name, String text) throws InvalidActionException {
        if (text.isBlank()) {
            throw new InvalidActionException("Parameter '" + name + "' must not be blank.");
        }
        if (text.length() > maxLength) {
            throw new InvalidActionException("Parameter '" + name + "' exceeds the maximum length of " + maxLength + ".");
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isISOControl(c)) {
                throw new InvalidActionException("Parameter '" + name + "' contains control characters.");
            }
            if (c == '/') {
                // tinystruct binds arguments from path segments, so a slash cannot be part of a value.
                throw new InvalidActionException("Parameter '" + name + "' must not contain '/'.");
            }
        }
    }

    private static void validateNumeric(String name, Class<?> type, String text) throws InvalidActionException {
        try {
            if (type == int.class || type == Integer.class) Integer.parseInt(text.trim());
            else if (type == long.class || type == Long.class) Long.parseLong(text.trim());
            else if (type == short.class || type == Short.class) Short.parseShort(text.trim());
            else if (type == byte.class || type == Byte.class) Byte.parseByte(text.trim());
            else if (type == float.class || type == Float.class || type == double.class || type == Double.class) {
                double d = Double.parseDouble(text.trim());
                if (Double.isNaN(d) || Double.isInfinite(d)) throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            throw new InvalidActionException("Parameter '" + name + "' is not a valid number.");
        }
    }
}
