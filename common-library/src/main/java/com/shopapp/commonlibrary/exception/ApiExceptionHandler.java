package com.shopapp.commonlibrary.exception;

import com.shopapp.commonlibrary.viewmodel.error.ErrorVm;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * Maps exceptions thrown by any controller to an {@link ErrorVm} response with a matching HTTP status.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    static final String INVALID_REQUEST_INFORMATION_MESSAGE = "Request information is not valid";
    static final String DATA_INTEGRITY_MESSAGE = "Request conflicts with existing data";

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorVm> handleNotFound(NotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), List.of(), ex);
    }

    @ExceptionHandler({BadRequestException.class, DuplicatedException.class,
        MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorVm> handleBadRequest(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of(), ex);
    }

    /** Malformed body or a path/query value of the wrong type: 400, without parser or conversion internals. */
    @ExceptionHandler({HttpMessageNotReadableException.class, TypeMismatchException.class})
    public ResponseEntity<ErrorVm> handleUnreadableRequest(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, INVALID_REQUEST_INFORMATION_MESSAGE, List.of(), ex);
    }

    /** The exception message carries SQL, constraint names and key values: never send it to the client. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorVm> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        return build(HttpStatus.BAD_REQUEST, DATA_INTEGRITY_MESSAGE, List.of(), ex);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorVm> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + " " + error.getDefaultMessage())
            .toList();
        return build(HttpStatus.BAD_REQUEST, INVALID_REQUEST_INFORMATION_MESSAGE, errors, ex);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorVm> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        List<String> errors = ex.getAllErrors().stream()
            .map(error -> error instanceof FieldError fieldError
                ? fieldError.getField() + " " + fieldError.getDefaultMessage()
                : error.getDefaultMessage())
            .toList();
        return build(HttpStatus.BAD_REQUEST, INVALID_REQUEST_INFORMATION_MESSAGE, errors, ex);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorVm> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> errors = ex.getConstraintViolations().stream()
            .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
            .toList();
        return build(HttpStatus.BAD_REQUEST, INVALID_REQUEST_INFORMATION_MESSAGE, errors, ex);
    }

    @ExceptionHandler(ResourceExistedException.class)
    public ResponseEntity<ErrorVm> handleResourceExisted(ResourceExistedException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), List.of(), ex);
    }

    @ExceptionHandler({AccessDeniedException.class, ForbiddenException.class, SignInRequiredException.class})
    public ResponseEntity<ErrorVm> handleForbidden(RuntimeException ex) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), List.of(), ex);
    }

    /** Spring Security method-security denials ({@code @PreAuthorize}) must stay 403, not fall through to 500. */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErrorVm> handleSecurityAccessDenied(
        org.springframework.security.access.AccessDeniedException ex) {
        HttpStatus status = HttpStatus.FORBIDDEN;
        return build(status, status.getReasonPhrase(), List.of(), ex);
    }

    @ExceptionHandler(InternalServerErrorException.class)
    public ResponseEntity<ErrorVm> handleInternalServerError(InternalServerErrorException ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), List.of(), ex);
    }

    /**
     * Spring MVC's own exceptions (unknown path, wrong method, malformed body, type mismatch, ...) implement
     * {@link ErrorResponse} and keep their 4xx status; anything else is an unexpected 500 without internals.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorVm> handleOther(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            String detail = errorResponse.getBody().getDetail();
            return build(status, detail != null ? detail : status.getReasonPhrase(), List.of(), ex);
        }
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        return build(status, status.getReasonPhrase(), List.of(), ex);
    }

    private ResponseEntity<ErrorVm> build(HttpStatus status, String detail, List<String> fieldErrors, Exception ex) {
        log.error("Request failed with {}: {}", status.value(), ex.getMessage(), ex);
        ErrorVm body = new ErrorVm(status.toString(), status.getReasonPhrase(), detail, fieldErrors);
        return ResponseEntity.status(status).body(body);
    }
}
