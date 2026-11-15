package com.shopapp.promotion.exception;

import com.shopapp.commonlibrary.viewmodel.error.ErrorVm;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Runs before the common ApiExceptionHandler so promotion rule failures carry their code. */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PromotionExceptionHandler {

    @ExceptionHandler(PromotionException.class)
    public ResponseEntity<ErrorVm> handlePromotion(PromotionException ex) {
        log.info("Promotion request refused: {}", ex.getMessage());
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
            .body(new ErrorVm(status.toString(), ex.getCode(), ex.getMessage(), List.of()));
    }
}
