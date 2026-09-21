package org.tinystruct.typesafe.workflow;

import org.tinystruct.workflow.event.WorkflowEvent;

/**
 * Resume vehicle for the {@code semantic-confirm} workflow.
 *
 * <p>Delivered via {@code engine.resume(id, event)} directly — NOT through
 * {@code EventDispatcher} (which is not restart-safe and swallows errors).
 *
 * @see org.tinystruct.typesafe.workflow.WorkflowConfirmationService
 */
public final class ConfirmationEvent extends WorkflowEvent<Boolean> {

    public ConfirmationEvent(String executionId, boolean confirmed) {
        super("ConfirmationEvent", executionId, confirmed);
    }

    /** {@code true} if the user confirmed; {@code false} if they rejected. */
    public boolean isConfirmed() {
        return Boolean.TRUE.equals(getPayload());
    }
}
