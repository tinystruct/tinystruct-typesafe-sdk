package org.tinystruct.typesafe.core.api;

import org.junit.jupiter.api.Test;
import org.tinystruct.typesafe.core.confirmation.PendingCall;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Result and pending-call text never carries argument values. */
class DispatchResultTest {

    @Test
    void dispatchResultToStringNeverContainsArgumentsButShowsTheOutcome() {
        assertTrue(DispatchResult.needsConfirmation("p").toString().contains("NEEDS_CONFIRMATION"));
        assertTrue(new PendingCall("a", Map.of("secret", "value"), "me", 0.9, 1, 2).toString().contains("a"));
        assertFalse(new PendingCall("a", Map.of("secret", "value"), "me", 0.9, 1, 2).toString().contains("value"));
    }
}
