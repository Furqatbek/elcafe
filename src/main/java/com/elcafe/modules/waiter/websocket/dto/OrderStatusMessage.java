package com.elcafe.modules.waiter.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * WebSocket message for order status updates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusMessage {
    private Long orderId;
    private String orderNumber;
    private String status;
    private Long tableId;
    private Integer tableNumber;
    private Long waiterId;
    private String waiterName;
    private String message;
    private LocalDateTime timestamp;
}
