package com.elcafe.modules.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * WebSocket message format for order events.
 * Based on CLIENT_RESTAURANT_FLOW.md documentation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEventMessage {

    /**
     * Event type: order.placed, order.accepted, order.preparing, order.ready,
     * order.picked_up, order.completed, order.cancelled, order.rejected
     */
    private String eventType;

    /**
     * Timestamp when event occurred
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    /**
     * Event data (varies by event type)
     */
    private Map<String, Object> data;
}
