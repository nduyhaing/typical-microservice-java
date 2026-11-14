package com.shopapp.commonlibrary.exception;

import com.shopapp.commonlibrary.utils.MessagesUtils;

public class DuplicatedException extends RuntimeException {

    public DuplicatedException(String errorCode, Object... args) {
        super(MessagesUtils.getMessage(errorCode, args));
    }
}
