package com.elcafe.modules.waiter.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket configuration for real-time waiter and kitchen communication
 * Enables STOMP messaging over WebSocket with SockJS fallback
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Configure message broker
     * - /topic/waiter: Broadcast waiter-related updates
     * - /topic/kitchen: Broadcast kitchen updates
     * - /topic/table: Broadcast table status updates
     * - /app: Prefix for messages routed to @MessageMapping methods
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple memory-based message broker to send messages to clients
        config.enableSimpleBroker("/topic", "/queue");

        // Define prefix for messages routed to @MessageMapping methods
        config.setApplicationDestinationPrefixes("/app");

        // Define prefix for user-specific destinations
        config.setUserDestinationPrefix("/user");
    }

    /**
     * Register STOMP endpoints
     * - /ws-waiter: Main WebSocket endpoint for waiter operations
     * - SockJS fallback enabled for browsers that don't support WebSocket
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-waiter")
                .setAllowedOriginPatterns("*") // Configure based on your CORS policy
                .withSockJS(); // Enable SockJS fallback options
    }
}
