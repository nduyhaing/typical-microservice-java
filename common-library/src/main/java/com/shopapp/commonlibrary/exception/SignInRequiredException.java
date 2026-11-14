package com.shopapp.commonlibrary.exception;

import com.shopapp.commonlibrary.utils.MessagesUtils;

public class SignInRequiredException extends RuntimeException {

    public SignInRequiredException(String errorCode, Object... args) {
        super(MessagesUtils.getMessage(errorCode, args));
    }
}
