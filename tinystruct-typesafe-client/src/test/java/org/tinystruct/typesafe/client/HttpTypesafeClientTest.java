package org.tinystruct.typesafe.client;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tinystruct.ApplicationException;
import org.tinystruct.data.component.Builder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises {@link HttpTypesafeClient} against a local server that plays the TypeSafe API. */
class HttpTypesafeClientTest {

    private static final String OK_BODY =
            "{\"model\":\"jev-1.13.0\",\"answers\":{\"__tool__\":{\"type\":\"choice\",\"choice\":\"create-user\","
                    + "\"confidence\":0.95}},\"usage\":{\"input_tokens\":12,\"output_tokens\":3}}";

    private HttpServer server;
    private final Deque<Integer> statuses = new ArrayDeque<>();
    private final Deque<String> bodies = new ArrayDeque<>();
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/systemone", exchange -> {
            requests.incrementAndGet();
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            int status = statuses.isEmpty() ? 200 : statuses.poll();
            byte[] body = (bodies.isEmpty() ? OK_BODY : bodies.poll()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private HttpTypesafeClient client(int retryMax) {
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/systemone";
        return new HttpTypesafeClient(endpoint, "secret-key", "jev-latest", 2000, 5000, retryMax, 1);
    }

    private static RoutingRequest request() {
        Builder questions = new Builder();
        Builder q = new Builder();
        q.put("type", "noul");
        q.put("instructions", "Is it?");
        questions.put("q", q);
        return new RoutingRequest("hello", questions, "jev-latest");
    }

    @Test
    void sendsBearerTokenAndTheDocumentedBody() throws Exception {
        RoutingResult result = client(0).classify(request());

        assertEquals("Bearer secret-key", lastAuthorization.get());
        Builder sent = new Builder();
        sent.parse(lastRequestBody.get());
        assertEquals("hello", sent.get("state").toString());
        assertEquals("jev-latest", sent.get("model").toString());
        assertTrue(sent.get("questions") instanceof Builder);

        assertEquals("create-user", result.getChoice("__tool__"));
        assertEquals("jev-1.13.0", result.getModelVersion());
        assertEquals(0.95, result.getChoiceConfidence("__tool__"), 1e-9);
    }

    @Test
    void retriesRateLimitThenSucceeds() throws Exception {
        statuses.add(429);
        bodies.add("{}");
        statuses.add(529);
        bodies.add("{}");

        RoutingResult result = client(3).classify(request());

        assertEquals(3, requests.get());
        assertEquals("create-user", result.getChoice("__tool__"));
    }

    @Test
    void givesUpAfterTheRetryBudgetIsSpent() {
        for (int i = 0; i < 5; i++) {
            statuses.add(429);
            bodies.add("{}");
        }
        ApplicationException e = assertThrows(ApplicationException.class, () -> client(2).classify(request()));
        assertEquals(3, requests.get(), "first attempt plus two retries");
        assertTrue(e.getMessage().contains("429"));
    }

    @Test
    void zeroRetriesMeansOneAttempt() {
        statuses.add(429);
        bodies.add("{}");
        assertThrows(ApplicationException.class, () -> client(0).classify(request()));
        assertEquals(1, requests.get());
    }

    @Test
    void unauthorizedIsNotRetried() {
        statuses.add(401);
        bodies.add("{}");
        ApplicationException e = assertThrows(ApplicationException.class, () -> client(3).classify(request()));
        assertEquals(1, requests.get());
        assertTrue(e.getMessage().contains("401"));
    }

    @Test
    void validationErrorIsNotRetriedAndItsBodyIsShortened() {
        statuses.add(422);
        bodies.add("x".repeat(1000));
        ApplicationException e = assertThrows(ApplicationException.class, () -> client(3).classify(request()));
        assertEquals(1, requests.get());
        assertTrue(e.getMessage().contains("422"));
        assertTrue(e.getMessage().length() < 400, "a long error body must not be copied whole");
    }

    @Test
    void unexpectedStatusFails() {
        statuses.add(500);
        bodies.add("boom");
        ApplicationException e = assertThrows(ApplicationException.class, () -> client(3).classify(request()));
        assertEquals(1, requests.get());
        assertTrue(e.getMessage().contains("500"));
    }

    @Test
    void blankApiKeyIsRejectedUpFront() {
        assertThrows(IllegalArgumentException.class,
                () -> new HttpTypesafeClient(null, " ", null, 0, 0, 0, 0));
    }

    @Test
    void invalidEndpointFails() {
        HttpTypesafeClient bad = new HttpTypesafeClient("not a url", "k", null, 0, 0, 0, 0);
        assertThrows(ApplicationException.class, () -> bad.classify(request()));
    }
}
