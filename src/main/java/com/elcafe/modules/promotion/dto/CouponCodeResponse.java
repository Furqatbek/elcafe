package com.elcafe.modules.promotion.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponCodeResponse {

    private Long id;
    private String code;
    private Long promotionId;
    private String promotionName;
    private Boolean singleUse;
    private Integer maxUses;
    private Integer usedCount;
    private Long assignedCustomerId;
    private String assignedCustomerName;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private Boolean active;
    private Boolean currentlyValid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
