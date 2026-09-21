package org.tinystruct.typesafe.core;

import org.tinystruct.AbstractApplication;
import org.tinystruct.ApplicationException;
import org.tinystruct.application.ActionRegistry;
import org.tinystruct.data.component.Builder;
import org.tinystruct.system.annotation.Action;
import org.tinystruct.system.annotation.Argument;
import org.tinystruct.typesafe.core.api.DispatchResult;

/**
 * The tinystruct entry point: thin {@code @Action} adapters over {@link TypesafeRuntime}.
 *
 * <pre>
 *   bin/dispatcher semantic --input "create an admin account for John"
 *   bin/dispatcher semantic/confirm/&lt;id&gt;
 *   bin/dispatcher semantic/reject/&lt;id&gt;
 *   bin/dispatcher typesafe/metrics
 * </pre>
 *
 * <p>The instruction is read from the {@code --input} option, not a path segment, so it may contain
 * slashes and spaces. Over HTTP pass it as the {@code --input} query parameter.
 *
 * <p>All behaviour lives in the runtime; this class only adapts it to tinystruct and formats the
 * result as JSON.
 */
public class SemanticDispatcher extends AbstractApplication {

    @Override
    public void init() {
        setTemplateRequired(false);
    }

    @Override
    public String version() {
        return "1.0.0-SNAPSHOT";
    }

    @Action(value = "semantic",
            description = "Dispatch a natural-language instruction to an allowlisted @Action.",
            options = {@Argument(key = "input", description = "The natural-language instruction.")})
    public Builder semantic() throws ApplicationException {
        Object input = getContext() == null ? null : getContext().getAttribute("--input");
        if (input == null || input.toString().isBlank()) {
            throw new ApplicationException("Missing --input. Example: bin/dispatcher semantic --input \"create user John\"");
        }
        DispatchResult result = runtime().router().route(input.toString(), ActionRegistry.getInstance(), getContext());
        return format(result);
    }

    @Action(value = "semantic/confirm",
            description = "Confirm a pending call by id.",
            arguments = {@Argument(key = "id", description = "The pending id returned by semantic.")})
    public Builder confirm(String id) throws ApplicationException {
        return format(runtime().confirmations().confirm(id, getContext()));
    }

    @Action(value = "semantic/reject",
            description = "Reject a pending call by id.",
            arguments = {@Argument(key = "id", description = "The pending id returned by semantic.")})
    public Builder reject(String id) throws ApplicationException {
        runtime().confirmations().reject(id, getContext());
        Builder b = new Builder();
        b.put("status", "CANCELLED");
        return b;
    }

    @Action(value = "typesafe/metrics", description = "Return the in-process routing metrics as JSON.")
    public Builder metrics() {
        return runtime().metrics().toJson();
    }

    private TypesafeRuntime runtime() {
        // An application that was not given a configuration uses the framework's own.
        return TypesafeRuntime.shared(getConfiguration() != null
                ? getConfiguration() : org.tinystruct.system.ApplicationManager.getConfiguration());
    }

    static Builder format(DispatchResult result) {
        Builder b = new Builder();
        b.put("status", result.getStatus().name());
        if (result.getPendingId() != null) b.put("pendingId", result.getPendingId());
        if (result.getReason() != null) b.put("reason", result.getReason());
        if (result.getResult() != null) b.put("result", result.getResult().toString());
        return b;
    }
}
