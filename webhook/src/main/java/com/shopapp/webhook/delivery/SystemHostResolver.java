package com.shopapp.webhook.delivery;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** The JVM's resolver (the container's DNS). */
public class SystemHostResolver implements HostResolver {

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }
}
