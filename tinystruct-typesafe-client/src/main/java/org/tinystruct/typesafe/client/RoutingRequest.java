package org.tinystruct.typesafe.client;

import org.tinystruct.data.component.Builder;

/**
 * Request payload for the TypeSafe /v1/systemone endpoint.
 *
 * <p>{@code state} is the user input (trusted text passed verbatim). {@code questions} is a
 * {@link Builder} object whose keys are question IDs and whose values are question descriptors.
 */
public class RoutingRequest {

    private final Object state;
    private final Builder questions;
    private final String model;

    public RoutingRequest(Object state, Builder questions, String model) {
        this.state = state;
        this.questions = questions;
        this.model = model;
    }

    public Object getState() {
        return state;
    }

    public Builder getQuestions() {
        return questions;
    }

    public String getModel() {
        return model;
    }

    /** Serialises this request to the JSON body expected by the TypeSafe API. */
    public String toJson() {
        Builder body = new Builder();
        if (state instanceof String) {
            body.put("state", state.toString());
        } else if (state instanceof Builder) {
            body.put("state", state);
        } else {
            body.put("state", state != null ? state.toString() : "");
        }
        body.put("questions", questions);
        if (model != null && !model.isBlank()) {
            body.put("model", model);
        }
        return body.toString();
    }
}
