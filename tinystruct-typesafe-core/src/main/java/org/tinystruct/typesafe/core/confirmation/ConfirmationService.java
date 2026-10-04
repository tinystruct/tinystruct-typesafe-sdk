package org.tinystruct.typesafe.core.confirmation;

import org.tinystruct.ApplicationException;
import org.tinystruct.system.Configuration;
import org.tinystruct.typesafe.core.api.DispatchResult;

import java.util.List;

/**
 * SPI for holding a validated call that awaits human confirmation.
 *
 * <p>Implementations are selected by class name through {@code typesafe.confirmation.service},
 * in the same way tinystruct selects {@code default.session.repository}. The implementation must
 * have a public no-argument constructor; {@link #configure} is then called with the application's
 * configuration.
 *
 * <p>Confirm and reject are an authorization boundary. Both must verify that {@code principal}
 * equals the principal captured when the call was opened, and that the call has not expired.
 */
public interface ConfirmationService {

    /** Called once after construction with the application's configuration. */
    default void configure(Configuration<String> configuration) {}

    /**
     * Persists the call and returns an identifier for it.
     *
     * @return the pending id
     */
    String open(PendingCall call) throws ApplicationException;

    /**
     * Executes a previously opened call.
     *
     * @throws PrincipalMismatchException     if {@code principal} is not the originator
     * @throws ConfirmationExpiredException   if the call has expired
     * @throws ConfirmationConflictException  if the call was already confirmed or cancelled
     */
    DispatchResult confirm(String pendingId, String principal) throws ApplicationException;

    /**
     * Cancels a previously opened call without executing it.
     *
     * @throws PrincipalMismatchException   if {@code principal} is not the originator
     * @throws ConfirmationExpiredException if the call has expired
     */
    void reject(String pendingId, String principal) throws ApplicationException;

    /**
     * The calls {@code principal} has open, newest first. Never another principal's: a listing is
     * not a way to learn what other people are about to do.
     *
     * <p>The default refuses, so a caller can tell "you have nothing pending" from "this
     * implementation cannot look".
     *
     * @throws UnsupportedOperationException if this implementation cannot enumerate pending calls
     */
    default List<PendingSummary> list(String principal) throws ApplicationException {
        throw new UnsupportedOperationException(getClass().getName() + " cannot list pending calls.");
    }

    /**
     * Discards the calls that expired without an answer, and returns how many were discarded.
     *
     * <p>A call nobody answers is never reached by {@link #confirm} or {@link #reject}, so without
     * this nothing ever removes it, or the arguments it holds. Implementations whose storage
     * expires entries by itself have nothing to do here.
     */
    default int sweepExpired() throws ApplicationException {
        return 0;
    }
}
