package com.elcafe.modules.loyalty.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TierRequest {

    @NotBlank
    private String name;

    @NotNull
    private Integer level;

    @PositiveOrZero
    private BigDecimal minTotalSpend;

    @PositiveOrZero
    private Integer minOrderCount;

    @PositiveOrZero
    private BigDecimal bonusMultiplier;

    private String benefitsDescription;
    private String color;
    private String icon;
}
