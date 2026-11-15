package com.shopapp.webhook.delivery;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Name resolution behind the SSRF guard (replaced in tests to simulate DNS answers and rebinding). */
public interface HostResolver {

    /** All addresses of {@code host} (a name or an IP literal without brackets). */
    InetAddress[] resolve(String host) throws UnknownHostException;
}
