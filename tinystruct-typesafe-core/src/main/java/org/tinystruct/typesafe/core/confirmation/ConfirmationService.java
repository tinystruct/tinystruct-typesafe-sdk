package org.tinystruct.typesafe.core.confirmation;

import org.tinystruct.ApplicationException;
import org.tinystruct.system.Configuration;
import org.tinystruct.typesafe.core.api.DispatchResult;

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
}
