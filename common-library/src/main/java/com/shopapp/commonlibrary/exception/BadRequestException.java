package com.shopapp.commonlibrary.exception;

import com.shopapp.commonlibrary.utils.MessagesUtils;

public class BadRequestException extends RuntimeException {

    public BadRequestException(String errorCode, Object... args) {
        super(MessagesUtils.getMessage(errorCode, args));
    }
}
