package org.tinystruct.typesafe.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the layout described in docs/Architecture.md: the sub-packages of {@code core} depend on
 * each other in one direction only, and nothing depends on the wiring in the root package.
 */
class PackageStructureTest {

    private static final String BASE = "org.tinystruct.typesafe.core";
    private static final Pattern IMPORT =
            Pattern.compile("^import\\s+" + Pattern.quote(BASE) + "\\.(?:(\\w+)\\.)?[A-Z]\\w*;", Pattern.MULTILINE);

    /** package (relative to core, "" for the root) → packages it imports from */
    private static Map<String, Set<String>> dependencies() throws IOException {
        Path sources = Path.of(System.getProperty("basedir", "."), "src", "main", "java",
                BASE.replace('.', '/'));
        Map<String, Set<String>> graph = new TreeMap<>();
        try (Stream<Path> files = Files.walk(sources)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                Path parent = sources.relativize(file).getParent();
                String owner = parent == null ? "" : parent.toString().replace('\\', '/').replace('/', '.');
                Matcher m = IMPORT.matcher(Files.readString(file));
                Set<String> imported = graph.computeIfAbsent(owner, k -> new TreeSet<>());
                while (m.find()) {
                    String target = m.group(1) == null ? "" : m.group(1);
                    if (!target.equals(owner)) imported.add(target);
                }
            }
        }
        return graph;
    }

    @Test
    void theSourceLayoutIsWhatTheDocumentationDescribes() throws IOException {
        Set<String> packages = dependencies().keySet();
        assertTrue(packages.containsAll(Set.of("", "api", "catalog", "candidate", "question", "argument",
                "policy", "execution", "confirmation", "cache", "metrics", "config", "pipeline")), packages.toString());
    }

    @Test
    void packagesDoNotDependOnEachOtherInCircles() throws IOException {
        Map<String, Set<String>> graph = dependencies();
        for (String start : graph.keySet()) {
            assertFalse(reaches(graph, start, start, new HashSet<>()),
                    "package '" + start + "' depends on itself through " + graph.get(start));
        }
    }

    @Test
    void nothingDependsOnTheWiringInTheRootPackage() throws IOException {
        dependencies().forEach((owner, imported) -> {
            if (!owner.isEmpty()) {
                assertFalse(imported.contains(""), "'" + owner + "' imports from the root package");
            }
        });
    }

    private static boolean reaches(Map<String, Set<String>> graph, String from, String target, Set<String> seen) {
        for (String next : graph.getOrDefault(from, Set.of())) {
            if (next.equals(target)) return true;
            if (seen.add(next) && reaches(graph, next, target, seen)) return true;
        }
        return false;
    }
}
