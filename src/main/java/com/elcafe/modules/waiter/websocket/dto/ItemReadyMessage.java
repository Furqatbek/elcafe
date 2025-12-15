package com.elcafe.modules.waiter.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * WebSocket message for item ready notifications from kitchen
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ItemReadyMessage {
    private Long orderId;
    private String orderNumber;
    private Long orderItemId;
    private String itemName;
    private Integer quantity;
    private Long tableId;
    private Integer tableNumber;
    private Long waiterId;
    private String waiterName;
    private String station; // GRILL, FRYER, SALAD, DESSERT, etc.
    private LocalDateTime timestamp;
}
