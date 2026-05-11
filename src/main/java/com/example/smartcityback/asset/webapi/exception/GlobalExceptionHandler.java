package com.example.smartcityback.asset.webapi.exception;

import com.example.smartcityback.asset.domain.exception.BusinessRuleViolationException;
import com.example.smartcityback.asset.domain.exception.NotFoundException;
import com.example.smartcityback.asset.domain.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private String requestId() {
        return MDC.get("requestId");
    }

    // ===============================
    // NOT FOUND
    // ===============================

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {

        ErrorResponse error = new ErrorResponse(
                ex.getErrorCode().name(),
                ex.getMessage(),
                HttpStatus.NOT_FOUND.value(),  // 404
                Instant.now(),
                requestId()
        );

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // ===============================
    // BUSINESS RULE VIOLATION
    // ===============================

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleViolationException ex) {

        ErrorResponse error = new ErrorResponse(
                ex.getErrorCode().name(),
                ex.getMessage(),
                HttpStatus.CONFLICT.value(),  // 409
                Instant.now(),
                requestId()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    // ===============================
    // VALIDATION
    // ===============================

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {

        ErrorResponse error = new ErrorResponse(
                ex.getErrorCode().name(),
                ex.getMessage(),
                HttpStatus.BAD_REQUEST.value(), // 400
                Instant.now(),
                requestId()
        );

        return ResponseEntity.badRequest().body(error);
    }

    // ===============================
    // SPRING @VALID ERRORS
    // ===============================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleSpringValidation(
            MethodArgumentNotValidException ex) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .findFirst()
                .orElse("Validation error");

        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(err -> err.getField() + "=" + err.getDefaultMessage())
                .toList();

        log.debug("ValidationFailed errors={}", errors);

        ErrorResponse error = new ErrorResponse(
                "VALIDATION_ERROR",
                message,
                HttpStatus.BAD_REQUEST.value(),  // 400
                Instant.now(),
                requestId()
        );

        return ResponseEntity.badRequest().body(error);
    }

    // ===============================
    // OPTIMISTIC LOCKING
    // ===============================

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {

        log.warn("OptimisticLockConflict entity={}", ex.getPersistentClassName());

        ErrorResponse error = new ErrorResponse(
                "CONCURRENT_MODIFICATION",
                "The resource was modified by another request. Please retry.",
                HttpStatus.CONFLICT.value(),  // 409
                Instant.now(),
                requestId()
        );

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    // ===============================
    // FALLBACK
    // ===============================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception ex) {

        ErrorResponse error = new ErrorResponse(
                "INTERNAL_ERROR",
                "Unexpected error",
                HttpStatus.INTERNAL_SERVER_ERROR.value(),  // 500
                Instant.now(),
                requestId()
        );

        log.error("UnexpectedError", ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error);
    }
}
