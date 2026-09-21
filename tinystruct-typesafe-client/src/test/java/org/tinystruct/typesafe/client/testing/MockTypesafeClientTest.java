package org.tinystruct.typesafe.client.testing;

import org.junit.jupiter.api.Test;
import org.tinystruct.ApplicationException;
import org.tinystruct.data.component.Builder;
import org.tinystruct.typesafe.client.RoutingRequest;
import org.tinystruct.typesafe.client.RoutingResult;

import static org.junit.jupiter.api.Assertions.*;

class MockTypesafeClientTest {

    private static RoutingRequest request() {
        return new RoutingRequest("hi", new Builder(), "jev-latest");
    }

    @Test
    void returnsCannedAnswersAndCountsCalls() throws Exception {
        MockTypesafeClient mock = new MockTypesafeClient("jev-test");
        mock.addChoiceAnswer("__tool__", "create-user", 0.95);
        mock.addNoulAnswer("create-user.admin", 0.7);

        RoutingResult result = mock.classify(request());

        assertEquals("create-user", result.getChoice("__tool__"));
        assertEquals(0.95, result.getChoiceConfidence("__tool__"), 1e-9);
        assertEquals(0.7, result.getNoul("create-user.admin"), 1e-9);
        assertEquals("jev-test", result.getModelVersion());
        assertEquals(1, mock.getCallCount());
    }

    @Test
    void removedAnswersAreAbsent() throws Exception {
        MockTypesafeClient mock = new MockTypesafeClient();
        mock.addChoiceAnswer("q", "a", 1.0);
        mock.removeAnswer("q");
        assertFalse(mock.classify(request()).hasAnswer("q"));
    }

    @Test
    void failsOnceWhenAsked() {
        MockTypesafeClient mock = new MockTypesafeClient();
        mock.failWith(new ApplicationException("down"));

        assertThrows(ApplicationException.class, () -> mock.classify(request()));
        assertDoesNotThrow(() -> mock.classify(request()), "the failure applies to one call only");
        assertEquals(2, mock.getCallCount());
    }
}
