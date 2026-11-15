package com.shopapp.webhook.delivery;

import com.shopapp.webhook.config.WebhookProperties;
import com.shopapp.webhook.utils.Constants;
import com.shopapp.webhook.utils.Constants.ErrorCode;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Keeps webhook deliveries off internal networks (decisions.md 5-33).
 * <ul>
 *   <li>Registration ({@link #checkForRegistration}): an absolute http(s) URL with a host, a valid port and no
 *       credentials, whose host resolves only to public addresses.</li>
 *   <li>Send time ({@link #resolveForConnect}): the HTTP client's DNS resolver; the host is resolved once, every
 *       address checked, and the connection made to exactly those addresses, so a name that rebinds to a private
 *       address after the check is never connected to.</li>
 * </ul>
 * Hosts on the allow-list ({@code shopapp.webhook.allowed-hosts}, exact names) skip the address check: a test
 * receiver on the compose network.
 */
public class SsrfGuard {

    private final HostResolver resolver;
    private final Set<String> allowedHosts;

    public SsrfGuard(HostResolver resolver, WebhookProperties properties) {
        this.resolver = resolver;
        this.allowedHosts = properties.allowedHostSet();
    }

    /** Refuses (with a 400 {@link UnsafeUrlException}) a URL that must not be registered. */
    public void checkForRegistration(String url) {
        URI uri = parse(url);
        String host = host(uri);
        if (isAllowListed(host)) {
            return;
        }
        InetAddress[] addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException ex) {
            throw new UnsafeUrlException(ErrorCode.WEBHOOK_URL_UNRESOLVABLE, host);
        }
        if (addresses.length == 0 || !allPublic(addresses)) {
            throw new UnsafeUrlException(ErrorCode.WEBHOOK_URL_BLOCKED, host);
        }
    }

    /**
     * Checks the URL's syntax again at send time (the row may predate a rule) and returns it.
     *
     * @throws UnsafeUrlException when it is not an acceptable http(s) URL
     */
    public URI checkSyntax(String url) {
        URI uri = parse(url);
        host(uri);
        return uri;
    }

    /**
     * Resolves a host for the HTTP client to connect to: the checked addresses, or a {@link BlockedAddressException}
     * when any of them is not public.
     */
    public InetAddress[] resolveForConnect(String host) throws UnknownHostException {
        String bare = stripBrackets(host);
        InetAddress[] addresses = resolver.resolve(bare);
        if (isAllowListed(bare)) {
            return addresses;
        }
        if (addresses.length == 0 || !allPublic(addresses)) {
            throw new BlockedAddressException("Refused to connect: " + bare + " resolves to a non-public address");
        }
        return addresses;
    }

    private boolean isAllowListed(String host) {
        return allowedHosts.contains(host.toLowerCase(Locale.ROOT));
    }

    private static boolean allPublic(InetAddress[] addresses) {
        for (InetAddress address : addresses) {
            if (!AddressPolicy.isPublic(address)) {
                return false;
            }
        }
        return true;
    }

    private static URI parse(String url) {
        if (url == null || url.isBlank() || url.length() > Constants.MAX_URL_LENGTH) {
            throw new UnsafeUrlException(ErrorCode.WEBHOOK_URL_INVALID);
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException ex) {
            throw new UnsafeUrlException(ErrorCode.WEBHOOK_URL_INVALID);
        }
        if (!uri.isAbsolute() || uri.isOpaque()) {
            throw new UnsafeUrlException(uri.getScheme() == null || isHttp(uri.getScheme())
                ? ErrorCode.WEBHOOK_URL_INVALID : ErrorCode.WEBHOOK_URL_SCHEME);
        }
        if (!isHttp(uri.getScheme())) {
            throw new UnsafeUrlException(ErrorCode.WEBHOOK_URL_SCHEME);
        }
        if (uri.getRawUserInfo() != null) {
            throw new UnsafeUrlException(ErrorCode.WEBHOOK_URL_CREDENTIALS);
        }
        int port = uri.getPort();
        if (port != -1 && (port < 1 || port > 65535)) {
            throw new UnsafeUrlException(ErrorCode.WEBHOOK_URL_INVALID);
        }
        return uri;
    }

    /** The host without IPv6 brackets; a URI without a (server-based) host is invalid. */
    private static String host(URI uri) {
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            // "user@host" with credentials is reported as such; anything else is malformed
            if (uri.getRawAuthority() != null && uri.getRawAuthority().contains("@")) {
                throw new UnsafeUrlException(ErrorCode.WEBHOOK_URL_CREDENTIALS);
            }
            throw new UnsafeUrlException(ErrorCode.WEBHOOK_URL_INVALID);
        }
        return stripBrackets(host);
    }

    private static String stripBrackets(String host) {
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    private static boolean isHttp(String scheme) {
        String lower = scheme.toLowerCase(Locale.ROOT);
        return "http".equals(lower) || "https".equals(lower);
    }
}
