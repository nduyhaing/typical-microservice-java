package com.shopapp.product.exception;

import com.shopapp.commonlibrary.viewmodel.error.ErrorVm;
import com.shopapp.product.utils.Constants;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Not enough stock: 400 whose {@code title} is the machine-readable code {@code NOT_ENOUGH_STOCK} and whose
 * {@code fieldErrors} name the product ({@code "productId 12"}), so the order service can tell it apart from a bad
 * request body (decisions.md 3c-21).
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StockExceptionHandler {

    @ExceptionHandler(NotEnoughStockException.class)
    public ResponseEntity<ErrorVm> handleNotEnoughStock(NotEnoughStockException ex) {
        log.info("Subtract refused: {}", ex.getMessage());
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ErrorVm(status.toString(), Constants.ErrorCode.NOT_ENOUGH_STOCK,
            ex.getMessage(), List.of("productId " + ex.getProductId())));
    }
}
