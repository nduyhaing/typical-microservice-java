package com.shopapp.payment.paypal.exception;

import com.shopapp.commonlibrary.viewmodel.error.ErrorVm;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** {@link PaypalException} as an {@link ErrorVm} with its own status; ahead of common-library's handler. */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PaypalExceptionHandler {

    @ExceptionHandler(PaypalException.class)
    public ResponseEntity<ErrorVm> handlePaypal(PaypalException ex) {
        log.warn("PayPal call failed with {}: {}", ex.getStatus().value(), ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
            .body(new ErrorVm(ex.getStatus().toString(), ex.getTitle(), ex.getMessage(), List.of()));
    }
}
