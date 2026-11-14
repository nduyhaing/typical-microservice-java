package com.shopapp.commonlibrary.exception;

import com.shopapp.commonlibrary.utils.MessagesUtils;

public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String errorCode, Object... args) {
        super(MessagesUtils.getMessage(errorCode, args));
    }
}
