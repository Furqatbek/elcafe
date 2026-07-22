package com.elcafe.modules.selfservice.exception;

/**
 * Base exception for all self-service related errors.
 */
public class SelfServiceException extends RuntimeException {

    public SelfServiceException(String message) {
        super(message);
    }

    public SelfServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
