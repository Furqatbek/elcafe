package com.elcafe.modules.instagram.dto;

/**
 * The outcome of a connection-test GET against Meta's Graph API for one config's stored credentials
 * (see {@code InstagramApiClient#verifyConnection}) — lets an operator confirm a saved token actually
 * works before a real customer DM fails on it, and see why when it does not.
 *
 * <p>Deliberately a separate type from {@link InstagramSendResult}, even though it reuses that type's
 * {@link InstagramSendResult.Failure} vocabulary and Meta-error-code classification ({@link
 * InstagramSendResult#classify}): this call has no recipient and delivers nothing, so {@code
 * delivered} would be a misleading name for the boolean here, and {@code RECIPIENT_UNAVAILABLE} has no
 * meaning for a plain account lookup. Reusing the enum (rather than inventing a parallel one) means a
 * dead token is reported as {@code TOKEN_INVALID} here exactly as it is for a send — the same signal,
 * the same {@code fatalForChannel()} semantics — which is what lets {@code InstagramBotConfigService}
 * reuse the V175 {@code tokenHealthy} flip for a failed manual test.
 *
 * @param ok         true when Meta accepted the GET and returned the account
 * @param failure    why not (null when {@code ok})
 * @param code       Meta's numeric error code, 0 when absent
 * @param message    Meta's human-readable error text, or a local reason (kill switch, malformed
 *                   config), null when {@code ok}
 * @param accountId  the Instagram account id Meta echoed back, null on failure
 * @param username   the username Meta echoed back, null on failure or when Meta omits it
 */
public record InstagramConnectionTestResult(
        boolean ok,
        InstagramSendResult.Failure failure,
        int code,
        String message,
        String accountId,
        String username) {

    public static InstagramConnectionTestResult ok(String accountId, String username) {
        return new InstagramConnectionTestResult(true, null, 0, null, accountId, username);
    }

    public static InstagramConnectionTestResult failed(InstagramSendResult.Failure failure, int code, String message) {
        return new InstagramConnectionTestResult(false, failure, code, message, null, null);
    }
}
