package com.shopapp.webhook.delivery;

import java.net.UnknownHostException;

/**
 * The host resolved to a non-public address at send time (e.g. DNS rebinding after registration): no connection is
 * made. An {@link UnknownHostException} so the HTTP client passes it through its resolver contract.
 */
public class BlockedAddressException extends UnknownHostException {

    public BlockedAddressException(String message) {
        super(message);
    }
}
