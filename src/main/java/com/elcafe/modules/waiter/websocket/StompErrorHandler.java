package com.elcafe.modules.waiter.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.messaging.StompSubProtocolErrorHandler;

import java.nio.charset.StandardCharsets;

/**
 * EH-1.5: STOMP-layer failures reach the client as a proper ERROR frame with a safe, short
 * message instead of an abrupt connection drop with no explanation. Auth rejections keep their
 * text (the client needs "unauthorized" to know it must re-CONNECT with a fresh token); anything
 * else is genericized so handler internals never leak into a frame.
 */
public class StompErrorHandler extends StompSubProtocolErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(StompErrorHandler.class);

    @Override
    public Message<byte[]> handleClientMessageProcessingError(Message<byte[]> clientMessage, Throwable ex) {
        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
        log.warn("STOMP client message failed: {}", cause.getMessage());

        boolean authError = cause instanceof org.springframework.security.core.AuthenticationException
                || cause instanceof org.springframework.security.access.AccessDeniedException;
        String message = authError && cause.getMessage() != null
                ? cause.getMessage()
                : "Request could not be processed";

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setMessage(message);
        accessor.setLeaveMutable(true);
        return org.springframework.messaging.support.MessageBuilder
                .createMessage(message.getBytes(StandardCharsets.UTF_8), accessor.getMessageHeaders());
    }
}
