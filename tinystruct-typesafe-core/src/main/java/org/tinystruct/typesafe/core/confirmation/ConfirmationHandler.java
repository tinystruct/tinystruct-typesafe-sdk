package org.tinystruct.typesafe.core.confirmation;

import org.tinystruct.ApplicationException;
import org.tinystruct.application.Context;
import org.tinystruct.typesafe.core.api.DispatchResult;
import org.tinystruct.typesafe.core.config.TypesafeConfig;
import org.tinystruct.typesafe.core.metrics.DispatchMetrics;

/**
 * Confirm and reject on behalf of the caller: resolves who is asking, delegates to the
 * {@link ConfirmationService}, and counts the outcome.
 *
 * <p>With no service configured both operations fail, so nothing can ever be confirmed that was
 * not held by a service.
 */
public final class ConfirmationHandler {

    private final ConfirmationService service; // nullable
    private final PrincipalResolver principals;
    private final DispatchMetrics metrics;

    public ConfirmationHandler(ConfirmationService service, PrincipalResolver principals, DispatchMetrics metrics) {
        this.service = service;
        this.principals = principals;
        this.metrics = metrics;
    }

    public DispatchResult confirm(String pendingId, Context context) throws ApplicationException {
        try {
            DispatchResult result = service().confirm(pendingId, principals.resolve(context));
            metrics.recordConfirmation();
            return result;
        } catch (ConfirmationExpiredException e) {
            metrics.recordExpiry();
            throw e;
        } catch (ConfirmationConflictException e) {
            metrics.recordConflict();
            throw e;
        } catch (PrincipalMismatchException e) {
            metrics.recordWrongPrincipal();
            throw e;
        }
    }

    public void reject(String pendingId, Context context) throws ApplicationException {
        try {
            service().reject(pendingId, principals.resolve(context));
            metrics.recordRejection();
        } catch (ConfirmationExpiredException e) {
            metrics.recordExpiry();
            throw e;
        } catch (PrincipalMismatchException e) {
            metrics.recordWrongPrincipal();
            throw e;
        }
    }

    private ConfirmationService service() throws ApplicationException {
        if (service == null) {
            throw new ApplicationException("No ConfirmationService is configured (" + TypesafeConfig.CONFIRMATION_SERVICE + ").");
        }
        return service;
    }
}
