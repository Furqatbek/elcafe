package com.elcafe.modules.waiter.dto;

import com.elcafe.modules.order.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecentTransactionData {
    private Long orderId;
    private String orderNumber;
    private Long tableId;
    private String tableNumber;
    private LocalDateTime createdAt;
    private OrderStatus status;
    private BigDecimal total;
}
