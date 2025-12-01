package com.elcafe.modules.waiter.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * WebSocket message for customer call waiter button
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallWaiterMessage {
    private Long tableId;
    private Integer tableNumber;
    private Long waiterId;
    private String waiterName;
    private String requestType; // SERVICE, BILL, COMPLAINT, QUESTION
    private String message;
    private LocalDateTime timestamp;
}
