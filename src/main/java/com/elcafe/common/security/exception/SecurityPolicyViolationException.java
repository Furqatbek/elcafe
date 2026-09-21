package com.elcafe.common.security.exception;

import lombok.Getter;

/**
 * Exception thrown when a security policy is violated.
 * Used for authorization failures, rate limiting, and policy violations.
 */
@Getter
public class SecurityPolicyViolationException extends RuntimeException {

    private final ViolationType violationType;
    private final String policyName;
    private final boolean requiresApproval;

    public SecurityPolicyViolationException(String message, ViolationType violationType) {
        super(message);
        this.violationType = violationType;
        this.policyName = null;
        this.requiresApproval = false;
    }

    public SecurityPolicyViolationException(String message, ViolationType violationType, String policyName) {
        super(message);
        this.violationType = violationType;
        this.policyName = policyName;
        this.requiresApproval = false;
    }

    public SecurityPolicyViolationException(String message, ViolationType violationType, boolean requiresApproval) {
        super(message);
        this.violationType = violationType;
        this.policyName = null;
        this.requiresApproval = requiresApproval;
    }

    public enum ViolationType {
        INSUFFICIENT_PERMISSIONS,
        AMOUNT_THRESHOLD_EXCEEDED,
        TIME_WINDOW_EXPIRED,
        PAYMENT_TYPE_RESTRICTION,
        RATE_LIMIT_EXCEEDED,
        APPROVAL_REQUIRED
    }
}
