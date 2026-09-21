package com.elcafe.modules.selfservice.exception;

/**
 * Thrown when a self-service session is not found or has expired.
 */
public class SessionNotFoundException extends SelfServiceException {

    public SessionNotFoundException(String sessionToken) {
        super("Session not found or expired: " + maskToken(sessionToken));
    }

    public SessionNotFoundException() {
        super("Session not found or expired");
    }

    private static String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "***";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }
}
