package com.shopapp.webhook.delivery;

import com.shopapp.commonlibrary.exception.BadRequestException;

/** A payload URL the SSRF guard refuses (a 400 when registering). */
public class UnsafeUrlException extends BadRequestException {

    public UnsafeUrlException(String errorCode, Object... args) {
        super(errorCode, args);
    }
}
