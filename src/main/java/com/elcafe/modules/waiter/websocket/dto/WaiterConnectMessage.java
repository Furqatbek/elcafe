package com.elcafe.modules.waiter.websocket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * WebSocket message for waiter connection/disconnection events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaiterConnectMessage {
    private Long waiterId;
    private String waiterName;
    private String role;
    private LocalDateTime timestamp;
}
