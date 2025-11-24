package com.elcafe.modules.courier.dto;

import com.elcafe.modules.courier.enums.TariffType;
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
public class TariffDTO {
    private Long id;
    private String name;
    private TariffType type;
    private String description;
    private BigDecimal fixedAmount;
    private BigDecimal amountPerOrder;
    private BigDecimal amountPerKilometer;
    private Integer minOrders;
    private Integer maxOrders;
    private BigDecimal minDistance;
    private BigDecimal maxDistance;
    private Integer minAttendanceDays;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
