package com.shopapp.commonlibrary.exception;

import com.shopapp.commonlibrary.utils.MessagesUtils;

public class ResourceExistedException extends RuntimeException {

    public ResourceExistedException(String errorCode, Object... args) {
        super(MessagesUtils.getMessage(errorCode, args));
    }
}
