package com.shopapp.order.exception;

import com.shopapp.commonlibrary.viewmodel.error.ErrorVm;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Runs before the common ApiExceptionHandler so a refused coupon carries promotion's code as the title. */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CouponExceptionHandler {

    @ExceptionHandler(CouponRejectedException.class)
    public ResponseEntity<ErrorVm> handleCouponRejected(CouponRejectedException ex) {
        log.info("Coupon refused: {} {}", ex.getCode(), ex.getMessage());
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
            .body(new ErrorVm(status.toString(), ex.getCode(), ex.getMessage(), List.of()));
    }
}
