package com.elcafe.modules.waiter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaiterCommissionSummaryDTO {
    private Long waiterId;
    private String waiterName;
    private BigDecimal currentCommissionPercent;
    private Boolean commissionEnabled;

    // Totals
    private Long totalCommissions;
    private BigDecimal totalOrderValue;
    private BigDecimal totalCommissionEarned;
    private BigDecimal pendingCommission;
    private BigDecimal approvedCommission;
    private BigDecimal paidCommission;

    // Period info
    private LocalDate periodStart;
    private LocalDate periodEnd;

    // Daily breakdown (optional)
    private List<DailyCommission> dailyCommissions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyCommission {
        private LocalDate date;
        private Long orderCount;
        private BigDecimal totalOrderValue;
        private BigDecimal commissionEarned;
    }
}
