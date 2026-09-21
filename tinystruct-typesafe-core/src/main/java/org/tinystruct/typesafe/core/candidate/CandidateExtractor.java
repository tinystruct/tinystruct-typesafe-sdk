package org.tinystruct.typesafe.core.candidate;

import org.tinystruct.typesafe.core.catalog.ParameterDefinition;

import java.util.List;

/**
 * Extracts candidate value spans from the user input for an open (free-text/number/date) parameter.
 *
 * <p>The default implementation ({@link TokenSpanExtractor}) returns contiguous token spans
 * up to 3 tokens, deduplicated, capped at 255. Pluggable per-parameter via regex or custom logic.
 */
public interface CandidateExtractor {

    /**
     * @param input     the verbatim user input
     * @param parameter the parameter to extract candidates for
     * @return candidate spans, deduplicated, no more than 255 entries; never {@code null}
     */
    List<ValueCandidate> extract(String input, ParameterDefinition parameter);
}
