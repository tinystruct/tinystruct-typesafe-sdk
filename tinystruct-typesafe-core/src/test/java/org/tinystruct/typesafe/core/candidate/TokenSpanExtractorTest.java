package org.tinystruct.typesafe.core.candidate;

import org.junit.jupiter.api.Test;
import org.tinystruct.typesafe.core.catalog.ParameterDefinition;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TokenSpanExtractorTest {

    private final TokenSpanExtractor extractor = new TokenSpanExtractor();
    private final ParameterDefinition fakeParam = new ParameterDefinition(
            "name", "name", ParameterDefinition.Kind.OPEN_VALUE, false, String.class, String.class, null);

    @Test
    void extractsSingleTokens() {
        List<ValueCandidate> candidates = extractor.extract("hello world", fakeParam);
        assertTrue(candidates.stream().anyMatch(c -> c.getValue().equals("hello")));
        assertTrue(candidates.stream().anyMatch(c -> c.getValue().equals("world")));
    }

    @Test
    void extractsMultiTokenSpans() {
        List<ValueCandidate> candidates = extractor.extract("create admin account for John", fakeParam);
        assertTrue(candidates.stream().anyMatch(c -> c.getValue().equals("for John")));
        assertTrue(candidates.stream().anyMatch(c -> c.getValue().equals("admin account")));
    }

    @Test
    void deduplicatesCandidates() {
        List<ValueCandidate> candidates = extractor.extract("John John", fakeParam);
        long johnCount = candidates.stream().filter(c -> c.getValue().equals("John")).count();
        assertEquals(1, johnCount, "Duplicates must be removed");
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertTrue(extractor.extract("", fakeParam).isEmpty());
        assertTrue(extractor.extract(null, fakeParam).isEmpty());
    }

    @Test
    void capsAt255Candidates() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            if (i > 0) sb.append(" ");
            sb.append("tok").append(i);
        }
        List<ValueCandidate> candidates = extractor.extract(sb.toString(), fakeParam);
        assertTrue(candidates.size() <= 255);
    }
}
