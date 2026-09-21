package org.tinystruct.typesafe.core.confirmation;

import org.tinystruct.application.Context;

/**
 * Resolves the caller''s principal identity from the current request context.
 *
 * <p>The default implementation returns the session attribute {@code userId} in HTTP mode
 * and a fixed {@code "cli"} principal in CLI mode. Pluggable via the class-name property
 * {@code typesafe.principal.resolver}.
 */
public interface PrincipalResolver {

    /**
     * @param context the current request context; may be a CLI or HTTP context
     * @return a non-null, non-blank string identifying the caller
     */
    String resolve(Context context);
}
