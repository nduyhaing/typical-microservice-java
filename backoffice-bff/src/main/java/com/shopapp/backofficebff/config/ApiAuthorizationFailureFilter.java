package com.shopapp.backofficebff.config;

import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

/**
 * When the gateway's token relay cannot get a usable access token for an {@code /api/**} call — typically because
 * the Keycloak session ended and the refresh failed — answer 401 JSON and drop the stale BFF session, instead of
 * a 500 or a redirect to the login page (which browser fetches cannot follow). The back office then asks the admin
 * to log in again.
 * Runs inside the Spring Security filter chain (lowest precedence), so it sees the error first.
 */
@Component
public class ApiAuthorizationFailureFilter implements WebFilter, Ordered {

    private static final String BODY = """
        {"statusCode":"401 UNAUTHORIZED","title":"Unauthorized",\
        "detail":"Your session has expired. Please log in again.","fieldErrors":[]}""";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!exchange.getRequest().getPath().value().startsWith("/api/")) {
            return chain.filter(exchange);
        }
        return chain.filter(exchange).onErrorResume(OAuth2AuthorizationException.class, error ->
            exchange.getSession().flatMap(WebSession::invalidate).then(writeUnauthorized(exchange.getResponse())));
    }

    /** Writes the 401 JSON body; also the anonymous {@code /api/**} entry point (decisions.md 3a-2). */
    static Mono<Void> writeUnauthorized(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer body = response.bufferFactory().wrap(BODY.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(body));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
