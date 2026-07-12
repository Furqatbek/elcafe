package com.elcafe.exception;

import com.elcafe.common.tenant.TenantInsertGuard;
import com.elcafe.modules.analytics.exception.AnalyticsCalculationException;
import com.elcafe.modules.order.exception.PaymentTransactionException;
import com.elcafe.utils.ApiResponse;
import jakarta.persistence.OptimisticLockException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

/**
 * One envelope for every API error (EH-0.2/EH-1.1, docs/ERROR_HANDLING_PLAN.md): correct status,
 * a stable {@link ErrorCode} the UI localizes, a requestId, and — for 5xx — a fixed generic
 * message so internals (stack traces, SQL, class names) never reach a client.
 *
 * <p>Logging convention: expected client errors log at {@code warn}; server faults log at
 * {@code error} with the full stack.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ---------------------------------------------------------------- typed business exceptions

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCode.NOT_FOUND, ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequestException(
            BadRequestException ex, WebRequest request) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorizedException(
            UnauthorizedException ex, WebRequest request) {
        log.warn("Unauthorized: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ErrorCode.UNAUTHENTICATED, ex.getMessage()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbiddenException(
            ForbiddenException ex, WebRequest request) {
        log.warn("Forbidden: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.FORBIDDEN, ex.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflictException(
            ConflictException ex, WebRequest request) {
        log.warn("Conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCode.CONFLICT, ex.getMessage()));
    }

    // ------------------------------------------------------------------------------- validation

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
            errors.put(fieldName, error.getDefaultMessage());
        });
        log.warn("Validation failed: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, "Validation failed", errors));
    }

    /** Bean Validation on @RequestParam/@PathVariable (method-level @Validated). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(v ->
                errors.put(String.valueOf(v.getPropertyPath()), v.getMessage()));
        log.warn("Constraint violation: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.VALIDATION_ERROR, "Validation failed", errors));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch for parameter '{}': {}", ex.getName(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST,
                        "Invalid value for parameter '" + ex.getName() + "'"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
            MissingServletRequestParameterException ex) {
        log.warn("Missing parameter: {}", ex.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST,
                        "Missing required parameter '" + ex.getParameterName() + "'"));
    }

    /** Malformed/unparseable JSON body. The parser message leaks class names — log it, don't return it. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Unreadable request body: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "Malformed request body"));
    }

    // --------------------------------------------------------------------- auth / access denial

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentialsException(
            BadCredentialsException ex, WebRequest request) {
        log.warn("Bad credentials: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ErrorCode.INVALID_CREDENTIALS, "Invalid credentials"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex, WebRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.FORBIDDEN, "Access denied"));
    }

    /** Account brute-force lockout (LoginAttemptService). 429 with the reason, so clients can back off. */
    @ExceptionHandler(org.springframework.security.authentication.LockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleLockedException(
            org.springframework.security.authentication.LockedException ex, WebRequest request) {
        log.warn("Login blocked (locked): {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(ErrorCode.ACCOUNT_LOCKED, ex.getMessage()));
    }

    /**
     * §3.4 tenant insert-guard: a write targeting a foreign restaurant. Map to 403 rather than the
     * default 500 so the client contract is clean once enforcement is on.
     */
    @ExceptionHandler(TenantInsertGuard.CrossTenantWriteException.class)
    public ResponseEntity<ApiResponse<Void>> handleCrossTenantWrite(
            TenantInsertGuard.CrossTenantWriteException ex, WebRequest request) {
        log.warn("Cross-tenant write blocked: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ErrorCode.TENANT_ACCESS_DENIED,
                        "You do not have access to this restaurant's data."));
    }

    // ------------------------------------------------------------------- conflicts / concurrency

    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class,
                       OptimisticLockingFailureException.class})
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleOptimisticLockException(
            Exception ex, WebRequest request) {
        log.warn("Concurrent modification detected: {}", ex.getMessage());
        Map<String, Object> details = new HashMap<>();
        details.put("retryable", true);
        details.put("errorCode", "CONCURRENT_MODIFICATION");
        details.put("message", "The resource was modified by another transaction. Please refresh and try again.");
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCode.CONCURRENT_MODIFICATION,
                        "Concurrent modification detected. Please retry.", details));
    }

    /**
     * DB constraint hit that no service-level check caught (unique/FK/not-null). 409 with a fixed
     * message — the SQL detail (constraint names, values) stays in the log.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.error("Data integrity violation", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCode.CONFLICT,
                        "The operation conflicts with existing data"));
    }

    // ----------------------------------------------------------------------- payments / domains

    @ExceptionHandler(PaymentTransactionException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handlePaymentTransactionException(
            PaymentTransactionException ex, WebRequest request) {
        log.error("Payment transaction failed: {} for order: {}, reason: {}",
                  ex.getMessage(), ex.getOrderId(), ex.getReason());

        Map<String, Object> details = new HashMap<>();
        details.put("orderId", ex.getOrderId());
        details.put("paymentId", ex.getPaymentId());
        details.put("transactionId", ex.getTransactionId());
        details.put("reason", ex.getReason() != null ? ex.getReason().name() : "UNKNOWN");
        details.put("retryable", isRetryablePaymentError(ex.getReason()));

        HttpStatus status = mapPaymentReasonToStatus(ex.getReason());
        return ResponseEntity.status(status)
                .body(ApiResponse.error(ErrorCode.PAYMENT_FAILED, ex.getMessage(), details));
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
            RateLimitExceededException ex, WebRequest request) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        Map<String, Object> details = new HashMap<>();
        details.put("retryable", true);
        details.put("errorCode", "RATE_LIMIT_EXCEEDED");
        details.put("retryAfterSeconds", 60);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "60")
                .body(ApiResponse.error(ErrorCode.RATE_LIMITED, ex.getMessage(), details));
    }

    @ExceptionHandler(AnalyticsCalculationException.class)
    public ResponseEntity<ApiResponse<Map<String, Object>>> handleAnalyticsCalculationException(
            AnalyticsCalculationException ex, WebRequest request) {
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

        return ResponseEntity.status(status)
                .body(ApiResponse.error(ErrorCode.ANALYTICS_FAILED, ex.getMessage(), details));
    }

    // -------------------------------------------------------------- routing / protocol / limits

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        log.warn("No resource for {} {}", ex.getHttpMethod(), ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ErrorCode.NOT_FOUND, "Resource not found"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        log.warn("Method not supported: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(ErrorCode.METHOD_NOT_ALLOWED, "Method not allowed"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex) {
        log.warn("Unsupported media type: {}", ex.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        log.warn("Upload too large: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(ErrorCode.FILE_TOO_LARGE, "The uploaded file is too large"));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMultipart(MultipartException ex) {
        log.warn("Multipart error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST, "Invalid file upload request"));
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<ApiResponse<Void>> handleAsyncTimeout(AsyncRequestTimeoutException ex) {
        log.warn("Async request timeout");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(ErrorCode.TIMEOUT, "The request timed out. Please try again."));
    }

    // ------------------------------------------------- bridges until the EH-1.3 raw-throw sweep

    /**
     * EH-1.2 bridge: services still throw IllegalArgumentException for business validation
     * ("Email already in use", "Invalid role. Must be one of…"). Those are human-written messages
     * and were mis-presenting as generic 500s. 400 + message until the sweep replaces them with
     * typed exceptions.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST,
                        ex.getMessage() != null ? ex.getMessage() : "Invalid request"));
    }

    /** EH-1.2 bridge: an IllegalStateException is a server-side invariant failure — never show it. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        log.error("Illegal state: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL, "An unexpected error occurred"));
    }

    // ---------------------------------------------------------------------------------- fallback

    /** Last resort: full stack to the log, fixed generic message + requestId to the client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGlobalException(
            Exception ex, WebRequest request) {
        log.error("Unexpected error: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL, "An unexpected error occurred"));
    }
}
