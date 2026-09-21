package org.tinystruct.typesafe.core.catalog;

import org.tinystruct.ApplicationException;
import org.tinystruct.system.cli.CommandArgument;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

/**
 * One parameter of a routable action, and how it is turned into TypeSafe questions.
 *
 * <p>Built from the {@link CommandArgument} that tinystruct records for every
 * {@code @Action(arguments = ...)} entry ({@link #from}), so nothing here inspects annotations or
 * methods itself.
 */
public final class ParameterDefinition {

    /** How a parameter is asked of TypeSafe. */
    public enum Kind {
        /** enum: one choice question over its constants. */
        ENUM_CHOICE,
        /** boolean: one noul question. */
        FLAG,
        /** {@code Set<Enum>} or {@code List<Enum>}: one noul question per constant. */
        SET_ENUM,
        /** String, number or Date: a choice over candidate spans of the input. */
        OPEN_VALUE
    }

    private final String name;
    private final String description;
    private final Kind kind;
    private final boolean optional;
    private final Class<?> rawType;
    private final Type genericType;
    private final Class<? extends Enum<?>> enumType;

    public ParameterDefinition(String name, String description, Kind kind, boolean optional,
                               Class<?> rawType, Type genericType, Class<? extends Enum<?>> enumType) {
        this.name = name;
        this.description = description == null ? "" : description;
        this.kind = kind;
        this.optional = optional;
        this.rawType = rawType;
        this.genericType = genericType;
        this.enumType = enumType;
    }

    /**
     * Classifies a framework command argument by its Java type.
     *
     * @throws ApplicationException if the framework recorded no type, or the type cannot be asked
     *                              of TypeSafe or bound from a path segment
     */
    @SuppressWarnings("unchecked")
    public static ParameterDefinition from(String actionPath, CommandArgument<String, Object> argument)
            throws ApplicationException {
        Type type = argument.getType();
        String name = argument.getKey();
        boolean optional = argument.isOptional();
        String description = argument.getDescription();

        if (type instanceof Class<?> raw) {
            if (raw.isEnum()) {
                return new ParameterDefinition(name, description, Kind.ENUM_CHOICE, optional,
                        raw, type, (Class<? extends Enum<?>>) raw);
            }
            if (raw == boolean.class || raw == Boolean.class) {
                return new ParameterDefinition(name, description, Kind.FLAG, optional, raw, type, null);
            }
            if (isOpenValue(raw)) {
                return new ParameterDefinition(name, description, Kind.OPEN_VALUE, optional, raw, type, null);
            }
        } else if (type instanceof ParameterizedType pt && pt.getRawType() instanceof Class<?> collection
                && (collection == Set.class || collection == List.class)
                && pt.getActualTypeArguments().length == 1
                && pt.getActualTypeArguments()[0] instanceof Class<?> element && element.isEnum()) {
            return new ParameterDefinition(name, description, Kind.SET_ENUM, optional,
                    collection, type, (Class<? extends Enum<?>>) element);
        }

        throw new ApplicationException("Parameter '" + name + "' of action '" + actionPath + "' has "
                + (type == null ? "no recorded Java type" : "unsupported type " + type.getTypeName())
                + ". Supported: enum, boolean, Set/List of enum, String, numbers, Date. "
                + "Declare every parameter of a semantically routed action in @Action(arguments = ...).");
    }

    private static boolean isOpenValue(Class<?> type) {
        return type == String.class
                || type == java.util.Date.class
                || (type.isPrimitive() && type != char.class && type != void.class)
                || (Number.class.isAssignableFrom(type) && type.getName().startsWith("java.lang."));
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Kind getKind() { return kind; }
    public boolean isOptional() { return optional; }
    public Class<?> getRawType() { return rawType; }
    public Type getGenericType() { return genericType; }

    /** The enum class for {@link Kind#ENUM_CHOICE} and {@link Kind#SET_ENUM}, otherwise {@code null}. */
    public Class<? extends Enum<?>> getEnumType() { return enumType; }

    @Override
    public String toString() {
        return "ParameterDefinition{name=" + name + ", kind=" + kind + ", optional=" + optional + "}";
    }
}
