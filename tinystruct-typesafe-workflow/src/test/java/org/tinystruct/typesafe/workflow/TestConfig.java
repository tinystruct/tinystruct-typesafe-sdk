package org.tinystruct.typesafe.workflow;

import org.tinystruct.system.Configuration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * An isolated in-memory {@link Configuration} for tests. tinystruct's {@code Settings} shares one
 * process-wide store and rewrites {@code application.properties} on every {@code set}, so tests keep
 * their own values elsewhere. Like {@code Settings}, an unset key reads as an empty string.
 */
final class TestConfig implements Configuration<String> {

    private final Map<String, String> values = new HashMap<>();

    static TestConfig of(String... keysAndValues) {
        TestConfig config = new TestConfig();
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
