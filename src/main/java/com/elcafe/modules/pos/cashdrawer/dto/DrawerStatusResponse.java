package com.elcafe.modules.pos.cashdrawer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DrawerStatusResponse {

    private Long drawerId;
    private String drawerName;
    private BigDecimal expectedFloat;
    private BigDecimal currentExpectedCash;
    private List<CashDrawerOperationDTO> recentOperations;
}
