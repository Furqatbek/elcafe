package com.elcafe.exception;

/**
 * Stable, machine-readable error codes — the contract half the frontend branches on
 * (docs/ERROR_HANDLING_PLAN.md, EH-0.1). The UI maps each code to a localized message
 * (i18n key {@code errors.<CODE>}), so backend {@code message} text is a fallback, never the
 * primary user-facing string.
 *
 * <p>Rules: never rename or reuse a constant (clients switch on the literal); add new ones freely.
 * {@code INTERNAL} must never carry internals in its message — generic text + requestId only.
 */
public enum ErrorCode {
    // 400
    VALIDATION_ERROR,
    BAD_REQUEST,
    // 401
    UNAUTHENTICATED,
    TOKEN_EXPIRED,
    INVALID_CREDENTIALS,
    // 402
    SUBSCRIPTION_INACTIVE,
    PAYMENT_FAILED,
    // 403
    FORBIDDEN,
    TENANT_ACCESS_DENIED,
    // 404
    NOT_FOUND,
    // 405 / 409 / 413 / 415
    METHOD_NOT_ALLOWED,
    CONFLICT,
    CONCURRENT_MODIFICATION,
    FILE_TOO_LARGE,
    UNSUPPORTED_MEDIA_TYPE,
    // 423 / 429
    ACCOUNT_LOCKED,
    RATE_LIMITED,
    // 5xx
    ANALYTICS_FAILED,
    TIMEOUT,
    INTERNAL
}
