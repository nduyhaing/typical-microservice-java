package com.shopapp.promotion.exception;

import com.shopapp.commonlibrary.viewmodel.error.ErrorVm;
import com.shopapp.promotion.utils.Constants.ErrorCode;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Runs before the common ApiExceptionHandler so promotion rule failures carry their code. A request that passed the
 * service's checks but lost a race is stopped by a unique index or check constraint: mapped to the same 400 code
 * (decisions.md 4-77); any other integrity violation stays a 500.
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PromotionExceptionHandler {

    /** Constraint name (lower case) → error code. */
    private static final Map<String, String> CONSTRAINT_CODES = Map.of(
        "ux_promotion_coupon_code", ErrorCode.COUPON_CODE_ALREADY_EXISTED,
        "ux_promotion_slug", ErrorCode.SLUG_ALREADY_EXISTED,
        "ck_promotion_usage_count", ErrorCode.USAGE_LIMIT_BELOW_USAGE_COUNT);

    private static final Map<String, String> CONSTRAINT_MESSAGES = Map.of(
        ErrorCode.COUPON_CODE_ALREADY_EXISTED, "This coupon code is already used by another promotion",
        ErrorCode.SLUG_ALREADY_EXISTED, "This slug is already used by another promotion",
        ErrorCode.USAGE_LIMIT_BELOW_USAGE_COUNT, "The usage limit cannot be below the number of uses already taken");

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorVm> handleIntegrity(DataIntegrityViolationException ex) {
        String code = constraintCode(ex);
        if (code == null) {
            throw ex;
        }
        log.info("Promotion request refused by constraint: {}", code);
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
            .body(new ErrorVm(status.toString(), code, CONSTRAINT_MESSAGES.get(code), List.of()));
    }

    static String constraintCode(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            String name = cause instanceof ConstraintViolationException violation
                ? violation.getConstraintName() : null;
            String text = (name != null ? name : String.valueOf(cause.getMessage())).toLowerCase(Locale.ROOT);
            for (Map.Entry<String, String> entry : CONSTRAINT_CODES.entrySet()) {
                if (text.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    @ExceptionHandler(PromotionException.class)
    public ResponseEntity<ErrorVm> handlePromotion(PromotionException ex) {
        log.info("Promotion request refused: {}", ex.getMessage());
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
            .body(new ErrorVm(status.toString(), ex.getCode(), ex.getMessage(), List.of()));
    }
}
