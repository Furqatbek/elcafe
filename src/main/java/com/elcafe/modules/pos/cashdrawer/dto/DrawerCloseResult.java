package com.elcafe.modules.pos.cashdrawer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrawerCloseResult {

    private Long drawerId;
    private BigDecimal countedAmount;
    private BigDecimal expectedAmount;
    private BigDecimal variance;
    private OffsetDateTime closedAt;
}
