package org.tinystruct.typesafe.core.confirmation;

import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.tinystruct.ApplicationContext;
import org.tinystruct.application.Context;
import org.tinystruct.http.Constants;
import org.tinystruct.http.Request;
import org.tinystruct.http.Session;
import org.tinystruct.typesafe.core.catalog.CallerMode;

import javax.crypto.SecretKey;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DefaultPrincipalResolverTest {

    private final DefaultPrincipalResolver resolver = new DefaultPrincipalResolver();

    private static Request<?, ?> requestWithSession(String sessionId, Map<String, Object> attributes) {
        Session session = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[]{Session.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getId" -> sessionId;
                    case "getAttribute" -> attributes.get((String) args[0]);
                    default -> null;
                });
        return (Request<?, ?>) Proxy.newProxyInstance(Request.class.getClassLoader(), new Class<?>[]{Request.class},
                (proxy, method, args) -> method.getName().equals("getSession") ? session : null);
    }

    @Test
    void noContextOrNoRequestMeansTheCommandLine() {
        assertEquals("cli", resolver.resolve(null));
        assertEquals("cli", resolver.resolve(new ApplicationContext()));
    }

    @Test
    void aForgedUserIdAttributeIsIgnored() {
        Context context = new ApplicationContext();
        context.setAttribute("userId", "admin");
        context.setAttribute("--userId", "admin");
        assertEquals("cli", resolver.resolve(context));
    }

    @Test
    void aValidatedJwtSubjectIsThePrincipal() {
        SecretKey key = Jwts.SIG.HS256.key().build();
        String token = Jwts.builder().subject("alice").signWith(key).compact();
        Jws<Claims> claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
        Context context = new ApplicationContext();
        context.setAttribute("CLAIMS", claims);

        assertEquals("jwt:alice", resolver.resolve(context));
    }

    @Test
    void theSessionUserIdIsThePrincipalWhenThereIsNoJwt() {
        Context context = new ApplicationContext();
        context.setAttribute(Constants.HTTP_REQUEST, requestWithSession("s-1", Map.of("userId", "42")));

        assertEquals("user:42", resolver.resolve(context));
    }

    @Test
    void anonymousCallersAreBoundToTheirOwnSession() {
        Context first = new ApplicationContext();
        first.setAttribute(Constants.HTTP_REQUEST, requestWithSession("s-1", new HashMap<>()));
        Context second = new ApplicationContext();
        second.setAttribute(Constants.HTTP_REQUEST, requestWithSession("s-2", new HashMap<>()));

        assertEquals("session:s-1", resolver.resolve(first));
        assertNotEquals(resolver.resolve(first), resolver.resolve(second),
                "one anonymous caller must not be able to confirm another's call");
    }

    @Test
    void aBlankUserIdFallsBackToTheSession() {
        Context context = new ApplicationContext();
        context.setAttribute(Constants.HTTP_REQUEST, requestWithSession("s-9", Map.of("userId", " ")));
        assertEquals("session:s-9", resolver.resolve(context));
    }

    @Test
    void callerModeFollowsTheRequestMethod() {
        assertEquals(org.tinystruct.system.annotation.Action.Mode.CLI, CallerMode.of(null));
        assertEquals(org.tinystruct.system.annotation.Action.Mode.CLI, CallerMode.of(new ApplicationContext()));

        Request<?, ?> post = (Request<?, ?>) Proxy.newProxyInstance(Request.class.getClassLoader(), new Class<?>[]{Request.class},
                (proxy, method, args) -> method.getName().equals("method") ? org.tinystruct.http.Method.POST : null);
        Context context = new ApplicationContext();
        context.setAttribute(Constants.HTTP_REQUEST, post);
        assertEquals(org.tinystruct.system.annotation.Action.Mode.HTTP_POST, CallerMode.of(context));
    }
}
