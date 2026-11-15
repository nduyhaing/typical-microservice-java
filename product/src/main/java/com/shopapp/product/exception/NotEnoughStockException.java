package com.shopapp.product.exception;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.product.utils.Constants;
import lombok.Getter;

/** A stock-tracked product has fewer units than a subtract-quantity call asked for. */
@Getter
public class NotEnoughStockException extends BadRequestException {

    private final Long productId;

    public NotEnoughStockException(Long productId) {
        super(Constants.ErrorCode.NOT_ENOUGH_STOCK, productId);
        this.productId = productId;
    }
}
