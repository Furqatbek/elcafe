package com.elcafe.modules.waiter.dto;

import com.elcafe.modules.waiter.enums.CommissionStatus;
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
public class WaiterCommissionDTO {
    private Long id;
    private Long waiterId;
    private String waiterName;
    private Long orderId;
    private String orderNumber;
    private Long restaurantId;
    private String restaurantName;
    private BigDecimal orderTotal;
    private BigDecimal commissionPercent;
    private BigDecimal commissionAmount;
    private CommissionStatus status;
    private Long payrollEntryId;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
