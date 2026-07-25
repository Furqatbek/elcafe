package com.elcafe.modules.instagram.dto;

/**
 * The outcome of one Meta Graph send, with enough detail for the caller to react.
 *
 * <p>Previously every failure collapsed into {@code false}: an expired access token, a customer who
 * blocked the business, a message outside Instagram's 24-hour window and a transient Meta 500 were
 * indistinguishable. That is why an operator could only ever see an unexplained low sent-count, and
 * why a broadcast kept hammering Meta with 10,000 doomed calls after the token had already died.
 *
 * @param delivered  true when Meta accepted the message
 * @param failure    why it did not (null when delivered)
 * @param code       Meta's numeric error code, 0 when absent — kept for logs and support tickets
 * @param message    Meta's human-readable error text, null when absent
 */
public record InstagramSendResult(boolean delivered, Failure failure, int code, String message) {

    /**
     * What kind of failure this was, in the terms a caller actually needs to make a decision.
     * The mapping from Meta's numeric codes lives in {@link #classify(int, int)}.
     */
    public enum Failure {
        /**
         * The access token is invalid or expired (Meta code 190). This kills the whole channel for
         * the restaurant, not just one message — every subsequent send will fail identically, so a
         * bulk operation should stop rather than continue.
         */
        TOKEN_INVALID,

        /**
         * This recipient cannot be messaged: they blocked the business, deleted their account, or
         * the conversation is outside the 24-hour messaging window (551, 10, 2534014). Permanent for
         * this subscriber, harmless for the rest of the run.
         */
        RECIPIENT_UNAVAILABLE,

        /** Throttled by Meta (613, 4, 17, 32, HTTP 429). Worth retrying later, never immediately. */
        RATE_LIMITED,

        /** Meta-side fault or a network problem (HTTP 5xx, transport errors). Retryable. */
        TRANSIENT,

        /** Our request was malformed (100, 2018001). Retrying will not help; fix the payload. */
        INVALID_REQUEST,

        /** The circuit breaker is open, so no call was attempted. */
        CIRCUIT_OPEN,

        /** Anything Meta returned that we have not mapped. Treated as non-retryable. */
        UNKNOWN;

        /** Whether retrying the same send later could plausibly succeed. */
        public boolean retryable() {
            return this == RATE_LIMITED || this == TRANSIENT || this == CIRCUIT_OPEN;
        }

        /** Whether this failure dooms every other send on the same config, not just this one. */
        public boolean fatalForChannel() {
            return this == TOKEN_INVALID;
        }
    }

    public static InstagramSendResult ok() {
        return new InstagramSendResult(true, null, 0, null);
    }

    public static InstagramSendResult failed(Failure failure, int code, String message) {
        return new InstagramSendResult(false, failure, code, message);
    }

    /**
     * Map a Meta error code to a {@link Failure}.
     *
     * <p>Codes are from Meta's Messenger Platform and Graph API error references. {@code subCode} is
     * checked first where it is more specific than the code (2534014 = "outside the allowed window"
     * arrives under the generic code 10).
     */
    public static Failure classify(int code, int subCode) {
        if (subCode == 2534014 || subCode == 2018278) {
            return Failure.RECIPIENT_UNAVAILABLE;   // outside 24h window / user unavailable
        }
        return switch (code) {
            case 190 -> Failure.TOKEN_INVALID;                 // expired / invalidated access token
            case 10, 551, 200, 230 -> Failure.RECIPIENT_UNAVAILABLE; // policy, blocked, no permission
            case 4, 17, 32, 613 -> Failure.RATE_LIMITED;       // app / page / custom rate limits
            case 1, 2 -> Failure.TRANSIENT;                    // unknown + temporary Meta faults
            case 100, 2018001 -> Failure.INVALID_REQUEST;      // malformed parameter
            default -> Failure.UNKNOWN;
        };
    }
}
