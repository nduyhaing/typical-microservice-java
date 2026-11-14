package com.shopapp.backofficebff.config;

import java.net.URI;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * CSRF protection for the cookie session (decisions.md 2b-7): state-changing requests are refused when the browser
 * says they come from another site. {@code Sec-Fetch-Site} must be same-origin (or absent / "none" for direct
 * navigation and non-browser clients), and an {@code Origin} header, when present, must equal the BFF's own origin.
 * This also blocks sibling subdomains (same-site but not same-origin), which SameSite=Lax cookies do not.
 * Runs before Spring Security; the request URI already reflects nginx's X-Forwarded-* headers.
 */
@Component
public class CrossSiteRequestFilter implements WebFilter, Ordered {

    private static final Set<HttpMethod> SAFE_METHODS = Set.of(HttpMethod.GET, HttpMethod.HEAD, HttpMethod.OPTIONS);
    private static final Set<String> ALLOWED_FETCH_SITES = Set.of("same-origin", "none");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (SAFE_METHODS.contains(request.getMethod()) || isSameOrigin(request)) {
            return chain.filter(exchange);
        }
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }

    private static boolean isSameOrigin(ServerHttpRequest request) {
        String fetchSite = request.getHeaders().getFirst("Sec-Fetch-Site");
        if (fetchSite != null && !ALLOWED_FETCH_SITES.contains(fetchSite)) {
            return false;
        }
        String origin = request.getHeaders().getOrigin();
        return origin == null || origin.equals(originOf(request.getURI()));
    }

    private static String originOf(URI uri) {
        int port = uri.getPort();
        boolean defaultPort = port == -1 || ("http".equals(uri.getScheme()) && port == 80)
            || ("https".equals(uri.getScheme()) && port == 443);
        return uri.getScheme() + "://" + uri.getHost() + (defaultPort ? "" : ":" + port);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
