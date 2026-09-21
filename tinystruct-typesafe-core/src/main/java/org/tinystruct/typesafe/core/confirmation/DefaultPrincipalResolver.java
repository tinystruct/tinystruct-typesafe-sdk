package org.tinystruct.typesafe.core.confirmation;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.tinystruct.application.Context;
import org.tinystruct.http.Constants;
import org.tinystruct.http.Request;
import org.tinystruct.http.Session;

/**
 * Identifies the caller from what tinystruct itself put in the {@link Context}, never from
 * request parameters a caller could forge:
 * <ol>
 *   <li>the subject of a validated JWT ({@code CLAIMS}, set by the HTTP server after it checks the token);</li>
 *   <li>the {@code userId} session attribute;</li>
 *   <li>otherwise the HTTP session itself, so anonymous callers can still only confirm their own calls;</li>
 *   <li>{@code cli} when there is no HTTP request.</li>
 * </ol>
 */
public final class DefaultPrincipalResolver implements PrincipalResolver {

    public static final String CLI_PRINCIPAL = "cli";

    @Override
    public String resolve(Context context) {
        if (context == null) return CLI_PRINCIPAL;

        if (context.getAttribute("CLAIMS") instanceof Jws<?> jws
                && jws.getPayload() instanceof Claims claims
                && claims.getSubject() != null && !claims.getSubject().isBlank()) {
            return "jwt:" + claims.getSubject();
        }

        if (context.getAttribute(Constants.HTTP_REQUEST) instanceof Request<?, ?> request) {
            Session session = request.getSession();
            if (session != null) {
                Object user = session.getAttribute("userId");
                if (user != null && !user.toString().isBlank()) return "user:" + user;
                if (session.getId() != null) return "session:" + session.getId();
            }
        }
        return CLI_PRINCIPAL;
    }
}
