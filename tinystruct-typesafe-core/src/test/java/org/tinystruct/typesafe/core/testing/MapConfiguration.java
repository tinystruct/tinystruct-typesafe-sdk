package org.tinystruct.typesafe.core.testing;

import org.tinystruct.system.Configuration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * An isolated in-memory {@link Configuration} for tests.
 *
 * <p>tinystruct's {@code Settings} shares one process-wide property store and rewrites
 * {@code application.properties} on every {@code set}, so tests must not use it to hold their own
 * values. Like {@code Settings}, an unset key reads as an empty string.
 */
public final class MapConfiguration implements Configuration<String> {

    private final Map<String, String> values = new HashMap<>();

    public static MapConfiguration of(String... keysAndValues) {
        MapConfiguration config = new MapConfiguration();
        for (int i = 0; i < keysAndValues.length; i += 2) config.set(keysAndValues[i], keysAndValues[i + 1]);
        return config;
    }

    @Override
    public void set(String name, String value) {
        values.put(name, value);
    }

    @Override
    public String get(String name) {
        return values.getOrDefault(name, "");
    }

    @Override
    public void remove(String name) {
        values.remove(name);
    }

    @Override
    public Set<String> propertyNames() {
        return new HashSet<>(values.keySet());
    }

    @Override
    public String getOrDefault(String name, String defaultValue) {
        return values.getOrDefault(name, defaultValue);
    }

    @Override
    public void setIfAbsent(String name, String value) {
        values.putIfAbsent(name, value);
    }

    @Override
    public String toString() {
        return values.toString();
    }
}
