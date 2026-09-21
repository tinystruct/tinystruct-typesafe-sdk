package org.tinystruct.typesafe.core.catalog;

import org.tinystruct.application.Context;
import org.tinystruct.http.Constants;
import org.tinystruct.http.Request;
import org.tinystruct.system.annotation.Action.Mode;

/** Works out which {@code @Action} mode the current caller is running in. */
public final class CallerMode {

    private CallerMode() {}

    /**
     * @return the HTTP method's mode when the context carries a request, otherwise {@link Mode#CLI}
     */
    public static Mode of(Context context) {
        if (context != null && context.getAttribute(Constants.HTTP_REQUEST) instanceof Request<?, ?> request) {
            return Mode.fromName(request.method().name());
        }
        return Mode.CLI;
    }

    /** {@code DEFAULT} always applies; any other declared mode must equal the caller's mode. */
    public static boolean allows(Mode declared, Mode caller) {
        return declared == Mode.DEFAULT || declared == caller;
    }
}
