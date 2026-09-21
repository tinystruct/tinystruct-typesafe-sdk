package org.tinystruct.typesafe.client;

import org.tinystruct.ApplicationException;
import org.tinystruct.net.URLHandler;
import org.tinystruct.net.URLHandlerFactory;
import org.tinystruct.net.URLRequest;
import org.tinystruct.net.URLResponse;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.logging.Logger;

/**
 * {@link TypesafeClient} that calls the TypeSafe REST API through tinystruct's own
 * {@link URLHandler} (no separate HTTP library).
 *
 * <p>Rate limiting ({@code 429}) and overload ({@code 529}) are retried with exponential backoff, as
 * the TypeSafe API reference recommends. Every other non-200 status fails immediately.
 */
public class HttpTypesafeClient implements TypesafeClient {

    public static final String DEFAULT_ENDPOINT = "https://api.typesafe.ai/v1/systemone";
    public static final String DEFAULT_MODEL = "jev-latest";

    /** Longest slice of an error body that is copied into an exception message. */
    private static final int MAX_ERROR_BODY = 200;

    private static final Logger LOGGER = Logger.getLogger(HttpTypesafeClient.class.getName());

    private final String endpoint;
    private final String apiKey;
    private final String model;
    private final int retryMax;
    private final long retryBackoffMs;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    /**
     * @param retryMax number of retries after the first attempt; a negative value selects the default of 3
     */
    public HttpTypesafeClient(String endpoint, String apiKey, String model,
                              int connectTimeoutMs, int readTimeoutMs,
                              int retryMax, long retryBackoffMs) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("TypeSafe API key must not be blank");
        }
        this.endpoint = endpoint != null && !endpoint.isBlank() ? endpoint : DEFAULT_ENDPOINT;
        this.apiKey = apiKey;
        this.model = model != null && !model.isBlank() ? model : DEFAULT_MODEL;
        this.connectTimeoutMs = connectTimeoutMs > 0 ? connectTimeoutMs : 5000;
        this.readTimeoutMs = readTimeoutMs > 0 ? readTimeoutMs : 30000;
        this.retryMax = retryMax >= 0 ? retryMax : 3;
        this.retryBackoffMs = retryBackoffMs > 0 ? retryBackoffMs : 1000;
    }

    @Override
    public RoutingResult classify(RoutingRequest request) throws ApplicationException {
        String body = request.toJson();
        RetryableException last = null;
        long backoff = retryBackoffMs;

        for (int attempt = 0; attempt <= retryMax; attempt++) {
            try {
                return RoutingResult.parse(send(body), model);
            } catch (RetryableException e) {
                last = e;
                if (attempt < retryMax) {
                    LOGGER.warning("TypeSafe is busy (attempt " + (attempt + 1) + "); backing off " + backoff + " ms");
                    sleep(backoff);
                    backoff *= 2;
                }
            }
        }
        throw new ApplicationException("TypeSafe still busy after " + (retryMax + 1) + " attempts: "
                + (last == null ? "" : last.getMessage()));
    }

    private String send(String jsonBody) throws ApplicationException {
        try {
            URL url = new URL(endpoint);
            URLRequest req = new URLRequest(url);
            req.setMethod("POST");
            req.setHeader("Authorization", "Bearer " + apiKey);
            req.setHeader("Content-Type", "application/json");
            req.setHeader("Accept", "application/json");
            req.setConnectTimeout(connectTimeoutMs);
            req.setReadTimeout(readTimeoutMs);
            req.setBody(jsonBody);

            URLResponse response = URLHandlerFactory.getHandler(url).handleRequest(req);
            int status = response.getStatusCode();
            String responseBody = response.getBody();

            return switch (status) {
                case 200 -> responseBody;
                case 401 -> throw new ApplicationException(
                        "TypeSafe authentication failed (401). Check typesafe.api-key.");
                case 422 -> throw new ApplicationException(
                        "TypeSafe rejected the request (422): " + abbreviate(responseBody));
                case 429, 529 -> throw new RetryableException(
                        "TypeSafe returned " + status + ": " + abbreviate(responseBody));
                default -> throw new ApplicationException(
                        "TypeSafe returned unexpected status " + status + ": " + abbreviate(responseBody));
            };
        } catch (MalformedURLException e) {
            throw new ApplicationException("Invalid TypeSafe endpoint URL: " + endpoint, e);
        }
    }

    /** Error bodies can echo the request, which contains user input, so only a short slice is kept. */
    private static String abbreviate(String body) {
        if (body == null) return "";
        return body.length() <= MAX_ERROR_BODY ? body : body.substring(0, MAX_ERROR_BODY) + "...";
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Signals a 429/529 response, the only kind that is retried. */
    private static final class RetryableException extends ApplicationException {
        RetryableException(String message) {
            super(message);
        }
    }
}
