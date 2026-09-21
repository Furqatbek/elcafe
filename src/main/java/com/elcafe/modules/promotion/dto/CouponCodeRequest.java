package com.elcafe.modules.promotion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponCodeRequest {

    @NotBlank(message = "Coupon code is required")
    private String code;

    @NotNull(message = "Promotion ID is required")
    private Long promotionId;

    @Builder.Default
    private Boolean singleUse = false;

    private Integer maxUses;

    private Long assignedCustomerId;

    private LocalDateTime validFrom;

    private LocalDateTime validUntil;

    @Builder.Default
    private Boolean active = true;
}
