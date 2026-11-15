package com.shopapp.promotion.exception;

import com.shopapp.commonlibrary.exception.BadRequestException;
import lombok.Getter;

/** A 400 whose error code goes to the client as the {@code title} of the ErrorVm (decisions.md 4-43). */
@Getter
public class PromotionException extends BadRequestException {

    private final String code;

    public PromotionException(String code, Object... args) {
        super(code, args);
        this.code = code;
    }
}
