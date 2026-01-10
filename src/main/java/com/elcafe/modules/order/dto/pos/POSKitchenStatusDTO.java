package com.elcafe.modules.order.dto.pos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class POSKitchenStatusDTO {

    private Long orderId;
    private String orderNumber;
    private String orderStatus;
    private Long kitchenOrderId;
    private String kitchenStatus;
    private String priority;
    private String assignedChef;
    private LocalDateTime preparationStartedAt;
    private LocalDateTime preparationCompletedAt;
    private Integer estimatedMinutes;
    private Integer actualMinutes;
}
