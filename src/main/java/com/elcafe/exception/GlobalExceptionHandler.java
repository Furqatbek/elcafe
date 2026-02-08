package com.elcafe.exception;

import com.elcafe.modules.analytics.exception.AnalyticsCalculationException;
import com.elcafe.modules.order.exception.PaymentTransactionException;
import com.elcafe.utils.ApiResponse;
import jakarta.persistence.OptimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(
            ResourceNotFoundException ex,
            WebRequest request
    ) {
        log.error("Resource not found: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequestException(
            BadRequestException ex,
            WebRequest request
    ) {
        log.error("Bad request: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedException(
            UnauthorizedException ex,
            WebRequest request
    ) {
        log.error("Unauthorized: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbiddenException(
            ForbiddenException ex,
            WebRequest request
    ) {
        log.error("Forbidden: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflictException(
            ConflictException ex,
            WebRequest request
    ) {
        log.error("Conflict: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationExceptions(
            MethodArgumentNotValidException ex
    ) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        log.error("Validation failed: {}", errors);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed", errors));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException(
            BadCredentialsException ex,
            WebRequest request
    ) {
        log.error("Bad credentials: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid credentials"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex,
            WebRequest request
    ) {
        log.error("Access denied: {}", ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied"));
    }

    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class,
                       OptimisticLockingFailureException.class})
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleOptimisticLockException(
            Exception ex,
            WebRequest request
    ) {
        log.warn("Concurrent modification detected: {}", ex.getMessage());
        Map<String, Object> details = new HashMap<>();
        details.put("retryable", true);
        details.put("errorCode", "CONCURRENT_MODIFICATION");
        details.put("message", "The resource was modified by another transaction. Please refresh and try again.");
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Concurrent modification detected. Please retry.", details));
    }

    @ExceptionHandler(PaymentTransactionException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handlePaymentTransactionException(
            PaymentTransactionException ex,
            WebRequest request
    ) {
        log.error("Payment transaction failed: {} for order: {}, reason: {}",
                  ex.getMessage(), ex.getOrderId(), ex.getReason());

        Map<String, Object> details = new HashMap<>();
        details.put("orderId", ex.getOrderId());
        details.put("paymentId", ex.getPaymentId());
        details.put("transactionId", ex.getTransactionId());
        details.put("reason", ex.getReason() != null ? ex.getReason().name() : "UNKNOWN");
        details.put("retryable", isRetryablePaymentError(ex.getReason()));

        HttpStatus status = mapPaymentReasonToStatus(ex.getReason());
        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(ex.getMessage(), details));
    }

    private boolean isRetryablePaymentError(PaymentTransactionException.PaymentFailureReason reason) {
        if (reason == null) return false;
        return switch (reason) {
            case NETWORK_ERROR, DATABASE_ERROR, CONCURRENT_MODIFICATION -> true;
            default -> false;
        };
    }

    private HttpStatus mapPaymentReasonToStatus(PaymentTransactionException.PaymentFailureReason reason) {
        if (reason == null) return HttpStatus.INTERNAL_SERVER_ERROR;
        return switch (reason) {
            case ORDER_STATUS_INVALID, INVALID_AMOUNT, INVALID_CARD -> HttpStatus.BAD_REQUEST;
            case ORDER_ALREADY_PAID -> HttpStatus.CONFLICT;
            case CONCURRENT_MODIFICATION -> HttpStatus.CONFLICT;
            case INSUFFICIENT_FUNDS, CARD_DECLINED -> HttpStatus.PAYMENT_REQUIRED;
            case FRAUD_DETECTED -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleRateLimitExceededException(
            RateLimitExceededException ex,
            WebRequest request
    ) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        Map<String, Object> details = new HashMap<>();
        details.put("retryable", true);
        details.put("errorCode", "RATE_LIMIT_EXCEEDED");
        details.put("retryAfterSeconds", 60);
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "60")
                .body(ApiResponse.error(ex.getMessage(), details));
    }

    @ExceptionHandler(AnalyticsCalculationException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleAnalyticsCalculationException(
            AnalyticsCalculationException ex,
            WebRequest request
    ) {
        log.warn("Analytics calculation failed for metric '{}': {}",
                 ex.getMetricName(), ex.getMessage());
        Map<String, Object> details = new HashMap<>();
        details.put("metricName", ex.getMetricName());
        details.put("restaurantId", ex.getRestaurantId());
        details.put("partialDataAvailable", ex.isPartialDataAvailable());
        details.put("errorCode", "ANALYTICS_CALCULATION_FAILED");

        // If partial data is available, return 206 Partial Content
        HttpStatus status = ex.isPartialDataAvailable()
                ? HttpStatus.PARTIAL_CONTENT
                : HttpStatus.INTERNAL_SERVER_ERROR;

        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(ex.getMessage(), details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGlobalException(
            Exception ex,
            WebRequest request
    ) {
        log.error("Unexpected error: ", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred"));
    }
}
