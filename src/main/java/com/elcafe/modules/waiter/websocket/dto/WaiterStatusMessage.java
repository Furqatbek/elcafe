package com.elcafe.modules.waiter.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * WebSocket message for waiter online/offline status
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaiterStatusMessage {
    private Long waiterId;
    private String waiterName;
    private String status; // ONLINE, OFFLINE, BUSY, ON_BREAK
    private LocalDateTime timestamp;
}
