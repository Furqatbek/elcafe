package com.elcafe.modules.waiter.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * WebSocket message for waiter requests (help, manager, etc.)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaiterRequestMessage {
    private Long waiterId;
    private String waiterName;
    private Long tableId;
    private Integer tableNumber;
    private String requestType; // HELP, MANAGER, BACKUP, BREAK
    private String message;
    private String priority; // LOW, NORMAL, HIGH, URGENT
    private LocalDateTime timestamp;
}
