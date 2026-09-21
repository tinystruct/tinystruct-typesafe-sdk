package org.tinystruct.typesafe.client.testing;

import org.tinystruct.ApplicationException;
import org.tinystruct.data.component.Builder;
import org.tinystruct.typesafe.client.RoutingRequest;
import org.tinystruct.typesafe.client.RoutingResult;
import org.tinystruct.typesafe.client.TypesafeClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Test-scope {@link TypesafeClient} that returns pre-programmed canned responses.
 *
 * <p>Usage:
 * <pre>
 * MockTypesafeClient mock = new MockTypesafeClient("jev-1.13.0");
 * mock.addChoiceAnswer("__tool__", "create-user", 0.95);
 * mock.addChoiceAnswer("create-user.role", "ADMIN", 0.92);
 * mock.addNoulAnswer("create-user.name?", 0.98);
 * </pre>
 */
public class MockTypesafeClient implements TypesafeClient {

    private final String modelVersion;
    final Builder cannedAnswers = new Builder();
    private int callCount = 0;
    private ApplicationException failWith = null;

    public MockTypesafeClient(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public MockTypesafeClient() {
        this("jev-1.13.0-mock");
    }

    /** Adds a canned choice answer with the given confidence. */
    public void addChoiceAnswer(String questionId, String choice, double confidence) {
        Builder answer = new Builder();
        answer.put("choice", choice);
        answer.put("confidence", confidence);
        cannedAnswers.put(questionId, answer);
    }

    /** Adds a canned noul answer. */
    public void addNoulAnswer(String questionId, double probability) {
        Builder answer = new Builder();
        answer.put("noul", probability);
        cannedAnswers.put(questionId, answer);
    }

    /** Removes a canned answer (simulates absent key). */
    public void removeAnswer(String questionId) {
        cannedAnswers.remove(questionId);
    }

    /** Configures this mock to throw the given exception on the next classify call. */
    public void failWith(ApplicationException ex) {
        this.failWith = ex;
    }

    /** Number of times {@link #classify} has been called. */
    public int getCallCount() {
        return callCount;
    }

    @Override
    public RoutingResult classify(RoutingRequest request) throws ApplicationException {
        callCount++;
        if (failWith != null) {
            ApplicationException ex = failWith;
            failWith = null;
            throw ex;
        }
        Builder usage = new Builder();
        usage.put("input_tokens", 0);
        usage.put("output_tokens", 0);
        return new RoutingResult(cannedAnswers, modelVersion, usage);
    }
}
