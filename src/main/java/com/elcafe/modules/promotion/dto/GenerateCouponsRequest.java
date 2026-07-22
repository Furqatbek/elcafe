package com.elcafe.modules.promotion.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class GenerateCouponsRequest {

    @NotNull(message = "Promotion ID is required")
    private Long promotionId;

    @NotNull(message = "Count is required")
    @Min(value = 1, message = "Count must be at least 1")
    @Max(value = 1000, message = "Cannot generate more than 1000 coupons at once")
    private Integer count;

    private String prefix; // Optional prefix for generated codes

    @Builder.Default
    private Integer codeLength = 8; // Length of the random part

    @Builder.Default
    private Boolean singleUse = true;

    private Integer maxUses;

    private LocalDateTime validFrom;

    private LocalDateTime validUntil;
}
