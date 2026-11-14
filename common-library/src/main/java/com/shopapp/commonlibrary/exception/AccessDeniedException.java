package com.shopapp.commonlibrary.exception;

import com.shopapp.commonlibrary.utils.MessagesUtils;

public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String errorCode, Object... args) {
        super(MessagesUtils.getMessage(errorCode, args));
    }
}
