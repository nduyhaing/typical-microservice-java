package com.shopapp.webhook.support;

import com.shopapp.webhook.delivery.HostResolver;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DNS for tests: mapped names answer their addresses (changeable at any time, to simulate rebinding); IP literals
 * and {@code localhost} resolve as usual; any other name is unknown.
 */
public class FakeHostResolver implements HostResolver {

    private final Map<String, InetAddress[]> names = new ConcurrentHashMap<>();

    /** Maps {@code host} to the given address literals (replacing an earlier mapping). */
    public FakeHostResolver map(String host, String... addresses) {
        InetAddress[] resolved = new InetAddress[addresses.length];
        try {
            for (int i = 0; i < addresses.length; i++) {
                resolved[i] = InetAddress.getByName(addresses[i]);
            }
        } catch (UnknownHostException ex) {
            throw new IllegalArgumentException(ex);
        }
        names.put(host.toLowerCase(Locale.ROOT), resolved);
        return this;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        InetAddress[] mapped = names.get(host.toLowerCase(Locale.ROOT));
        if (mapped != null) {
            return mapped.clone();
        }
        if ("localhost".equalsIgnoreCase(host) || host.chars().allMatch(c -> Character.isDigit(c) || c == '.')
            || host.contains(":")) {
            return InetAddress.getAllByName(host);
        }
        throw new UnknownHostException(host);
    }
}
