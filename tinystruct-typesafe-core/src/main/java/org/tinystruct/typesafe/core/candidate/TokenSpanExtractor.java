package org.tinystruct.typesafe.core.candidate;

import org.tinystruct.typesafe.core.catalog.ParameterDefinition;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default {@link CandidateExtractor}: extracts contiguous token spans up to 3 tokens,
 * deduplicated, capped at 255.
 *
 * <p>A "token" is a maximal sequence of non-whitespace characters. This extractor
 * deliberately over-finds, letting Jev select the best candidate.
 */
public final class TokenSpanExtractor implements CandidateExtractor {

    public static final int MAX_SPAN_TOKENS = 3;
    public static final int MAX_CANDIDATES = 255;

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\S+");

    @Override
    public List<ValueCandidate> extract(String input, ParameterDefinition parameter) {
        if (input == null || input.isBlank()) {
            return List.of();
        }

        List<int[]> tokenPositions = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        Matcher m = TOKEN_PATTERN.matcher(input);
        while (m.find()) {
            tokenPositions.add(new int[]{m.start(), m.end()});
            tokens.add(m.group());
        }

        Set<String> seen = new LinkedHashSet<>();
        List<ValueCandidate> candidates = new ArrayList<>();

        outer:
        for (int start = 0; start < tokens.size(); start++) {
            for (int len = 1; len <= MAX_SPAN_TOKENS && start + len <= tokens.size(); len++) {
                int charStart = tokenPositions.get(start)[0];
                int charEnd = tokenPositions.get(start + len - 1)[1];
                String span = input.substring(charStart, charEnd);
                if (seen.add(span)) {
                    candidates.add(new ValueCandidate(span, charStart, charEnd));
                    if (candidates.size() >= MAX_CANDIDATES) {
                        break outer;
                    }
                }
            }
        }

        return candidates;
    }
}
