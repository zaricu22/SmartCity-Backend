package com.example.smartcityback.asset.webapi.exception;

import com.example.smartcityback.asset.domain.exception.BusinessRuleViolationException;
import com.example.smartcityback.asset.domain.exception.NotFoundException;
import com.example.smartcityback.asset.domain.exception.ValidationException;
import com.example.smartcityback.asset.webapi.response.ErrorResponse;
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

// HTTP status tier design:
//   400 Bad Request          — malformed at protocol level (unparseable JSON, wrong Content-Type,
//                              missing body); handled automatically by Spring's DefaultHandlerExceptionResolver
//                              via HttpMessageNotReadableException before reaching this handler
//   422 Unprocessable Entity — syntactically valid request that fails validation: missing required
//                              fields (@Valid) or domain structural invariants (ValidationException)
//   409 Conflict             — valid and parseable request blocked by current resource state:
//                              domain business rule violation or optimistic lock conflict
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Every error response carries the requestId so that callers can include it in support
    // tickets, and so operations teams can correlate the client-facing error with the full
    // server-side log trace across all layers (RequestIdFilter → controller → service →
    // repository). The value is written to MDC by RequestIdFilter at the start of each
    // request and is read here via MDC.get() rather than being passed as a method argument,
    // to avoid threading it through every exception handler signature.
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
                HttpStatus.CONFLICT.value(),  // 409 — valid request, blocked by current domain state
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
                HttpStatus.UNPROCESSABLE_ENTITY.value(), // 422 — valid JSON, fails domain structural invariant
                Instant.now(),
                requestId()
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
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
                HttpStatus.UNPROCESSABLE_ENTITY.value(), // 422 — valid JSON, fails @Valid field constraint
                Instant.now(),
                requestId()
        );

        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
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
                HttpStatus.CONFLICT.value(),  // 409 — valid request, blocked by concurrent modification
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

        // "Unexpected error" is intentionally vague: returning the real exception message or
        // a stack trace in a 500 response would expose internal details to callers — JPA class
        // names, SQL fragments, file paths, or library internals — which is a security risk.
        // The full exception is always logged server-side via log.error() so nothing is lost
        // for diagnosis. Callers receive only the requestId to correlate with support.
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
