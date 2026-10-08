package com.thedavelopers.eventqr.shared.exceptions;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.persistence.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.transaction.TransactionSystemException;

import com.thedavelopers.eventqr.shared.response.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, exception.getMessage(), request);
    }

    @ExceptionHandler({BadRequestException.class, ConflictException.class, IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(RuntimeException exception, HttpServletRequest request) {
        HttpStatus status = exception instanceof ConflictException ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        return build(status, exception.getMessage(), request);
    }

    @ExceptionHandler({DataIntegrityViolationException.class, PersistenceException.class, JpaSystemException.class, TransactionSystemException.class})
    public ResponseEntity<ErrorResponse> handleDataIntegrity(RuntimeException exception, HttpServletRequest request) {
        String detail = rootCauseMessage(exception);
        log.warn("Data integrity error path={} detail={}", request.getRequestURI(), detail);
        if (request.getRequestURI() != null
                && request.getRequestURI().matches(".*/api/v1/organizer/events/.*/staff$")) {
            if (detail != null && detail.toLowerCase().contains("role_label")) {
                return build(HttpStatus.CONFLICT, "Staff assignment failed: role label is required.", request);
            }
            return build(HttpStatus.CONFLICT, "Staff assignment failed due to invalid assignment data.", request);
        }
        if (request.getRequestURI() != null
                && request.getRequestURI().matches(".*/api/v1/staff/events/.*/scan/.*|.*/api/v1/transactions$")) {
            if (detail != null && detail.toLowerCase().contains("metadata")) {
                return build(HttpStatus.CONFLICT, "Transaction failed: metadata missing.", request);
            }
            return build(HttpStatus.CONFLICT, "Transaction failed due to invalid transaction data.", request);
        }
        return build(HttpStatus.CONFLICT, "Registration failed. Please try again.", request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException exception, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, exception.getMessage(), request);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException exception, HttpServletRequest request) {
        ResponseEntity<ErrorResponse> response = build(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage(), request);
        if (exception.getRetryAfterSeconds() <= 0) {
            return response;
        }
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
                .body(response.getBody());
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException exception, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", HttpStatus.BAD_REQUEST.getReasonPhrase());
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        // Hibernate Validator reports violations as a Set, so sort for a stable "first" message and keep the
        // first message per field.
        exception.getBindingResult().getFieldErrors().stream()
                .sorted(java.util.Comparator.comparing(FieldError::getField)
                        .thenComparing(fe -> String.valueOf(fe.getDefaultMessage())))
                .forEach(fe -> fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        // Clients show "message": surface the first concrete reason (e.g. the password policy text).
        String firstReason = fieldErrors.values().stream().filter(m -> m != null && !m.isBlank()).findFirst()
                .orElse("Validation failed");
        body.put("message", firstReason);
        body.put("fieldErrors", fieldErrors);
        body.put("path", request.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception exception, HttpServletRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again.", request);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage();
    }
}

