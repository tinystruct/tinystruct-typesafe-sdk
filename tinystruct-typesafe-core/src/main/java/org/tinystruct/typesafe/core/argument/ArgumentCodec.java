package org.tinystruct.typesafe.core.argument;

import org.tinystruct.data.component.Builder;
import org.tinystruct.typesafe.core.catalog.ActionDefinition;
import org.tinystruct.typesafe.core.catalog.ParameterDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts resolved arguments to and from a {@link Builder}, so a pending call can be persisted
 * and later restored <em>with its original types</em>.
 *
 * <p>Encoding: enum → constant name, {@code Set}/{@code List} of enum → comma-separated names,
 * boolean → boolean, text/number → string. Decoding uses the {@link ActionDefinition} to rebuild
 * enums and collections, and throws {@link InvalidActionException} for anything that no longer
 * fits the current action signature (for example an enum constant that has since been removed).
 */
public final class ArgumentCodec {

    private ArgumentCodec() {}

    public static Builder encode(Map<String, Object> arguments) {
        Builder out = new Builder();
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                out.put(name, (String) null);
            } else if (value instanceof Boolean flag) {
                out.put(name, flag);
            } else if (value instanceof Enum<?> constant) {
                out.put(name, constant.name());
            } else if (value instanceof Collection<?> members) {
                StringBuilder names = new StringBuilder();
                for (Object member : members) {
                    if (names.length() > 0) names.append(',');
                    names.append(member instanceof Enum<?> e ? e.name() : String.valueOf(member));
                }
                out.put(name, names.toString());
            } else {
                out.put(name, String.valueOf(value));
            }
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Map<String, Object> decode(ActionDefinition action, Builder encoded) throws InvalidActionException {
        Map<String, Object> out = new LinkedHashMap<>();
        for (ParameterDefinition param : action.getParameters()) {
            Object raw = encoded.containsKey(param.getName()) ? encoded.get(param.getName()) : null;
            if (raw == null) {
                out.put(param.getName(), null);
                continue;
            }
            String text = raw.toString();
            switch (param.getKind()) {
                case ENUM_CHOICE -> out.put(param.getName(), toEnum(param, text));
                case FLAG -> out.put(param.getName(), Boolean.parseBoolean(text));
                case SET_ENUM -> {
                    List<Enum<?>> members = new ArrayList<>();
                    for (String name : text.split(",")) {
                        if (!name.isBlank()) members.add(toEnum(param, name.trim()));
                    }
                    if (List.class.isAssignableFrom(param.getRawType())) {
                        out.put(param.getName(), members);
                    } else {
                        Set<Enum<?>> set = new LinkedHashSet<>(members);
                        out.put(param.getName(), set);
                    }
                }
                case OPEN_VALUE -> out.put(param.getName(), text);
            }
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Enum<?> toEnum(ParameterDefinition param, String name) throws InvalidActionException {
        try {
            return Enum.valueOf((Class<? extends Enum>) param.getEnumType(), name);
        } catch (IllegalArgumentException e) {
            throw new InvalidActionException("Parameter '" + param.getName() + "' has a value that is no longer valid.");
        }
    }
}
