package com.elcafe.utils;

import com.elcafe.common.observability.RequestIdFilter;
import com.elcafe.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private Object errors;
    /**
     * Stable machine-readable code (EH-0.1, docs/ERROR_HANDLING_PLAN.md). Clients branch on this,
     * never on {@code message} text; the UI localizes it via the {@code errors.<CODE>} i18n keys.
     */
    private String error;
    /** Correlation id (X-Request-Id / MDC) so a user-visible error can be matched to server logs. */
    private String requestId;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /** Legacy code-less error (EH-1 migrates callers to the {@link ErrorCode} overloads). */
    public static <T> ApiResponse<T> error(String message) {
        return error(null, message, null);
    }

    /** Legacy code-less error with details (EH-1 migrates callers to the {@link ErrorCode} overloads). */
    public static <T> ApiResponse<T> error(String message, Object errors) {
        return error(null, message, errors);
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message) {
        return error(code, message, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message, Object errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(code != null ? code.name() : null)
                .message(message)
                .errors(errors)
                .requestId(MDC.get(RequestIdFilter.MDC_KEY))
                .timestamp(LocalDateTime.now())
                .build();
    }
}
