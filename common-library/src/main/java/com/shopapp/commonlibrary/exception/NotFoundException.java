package com.shopapp.commonlibrary.exception;

import com.shopapp.commonlibrary.utils.MessagesUtils;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String errorCode, Object... args) {
        super(MessagesUtils.getMessage(errorCode, args));
    }
}
