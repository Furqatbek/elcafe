package com.elcafe.modules.waiter.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * WebSocket message for table status updates
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableStatusMessage {
    private Long tableId;
    private Integer tableNumber;
    private String status;
    private String section;
    private String floor;
    private Long waiterId;
    private String waiterName;
    private Integer capacity;
    private LocalDateTime timestamp;
}
