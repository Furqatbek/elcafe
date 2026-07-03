package com.elcafe.modules.waiter.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
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
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    /**
     * Authenticate + tenant-authorize the STOMP inbound channel (audit #21). Servlet filters never see
     * STOMP frames, so CONNECT auth and per-subscription tenant checks live here.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    /**
     * Configure message broker. Order/kitchen/table/waiter streams are tenant-scoped under
     * {@code /topic/restaurant/{restaurantId}/...} (kitchen, table, waiter/orders, waiter/requests,
     * waiter/calls, waiter/status); {@link StompAuthChannelInterceptor} tenant-checks subscriptions and
     * refuses the retired bare global topics.
     * - /topic: Broadcast destinations (tenant-scoped under /topic/restaurant/{id}/...)
     * - /queue + /user: user-specific destinations (e.g. /user/queue/notifications)
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
     * - /ws-print-agent: WebSocket endpoint for print agent connections
     * - SockJS fallback enabled for browsers that don't support WebSocket
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-waiter")
                .setAllowedOriginPatterns("*") // Configure based on your CORS policy
                .withSockJS(); // Enable SockJS fallback options

        // Print agent endpoint - no SockJS needed as agent is a dedicated application
        registry.addEndpoint("/ws-print-agent")
                .setAllowedOriginPatterns("*");
    }
}
