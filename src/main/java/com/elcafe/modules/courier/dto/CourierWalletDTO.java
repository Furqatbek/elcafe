package com.elcafe.modules.courier.dto;

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
public class CourierWalletDTO {
    private Long id;
    private Long courierProfileId;
    private String courierName;
    private BigDecimal balance;
    private BigDecimal totalEarned;
    private BigDecimal totalWithdrawn;
    private BigDecimal totalBonuses;
    private BigDecimal totalFines;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
