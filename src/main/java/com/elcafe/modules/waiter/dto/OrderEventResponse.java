package com.elcafe.modules.waiter.dto;

import com.elcafe.modules.waiter.enums.OrderEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEventResponse {
    private Long id;
    private Long orderId;
    private OrderEventType eventType;
    private String triggeredBy;
    private String metadata;
    private LocalDateTime createdAt;
}
