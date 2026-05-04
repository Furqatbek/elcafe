package com.elcafe.modules.pos.shift.dto;

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
public class ShiftHandoverDTO {

    private Long outgoingShiftId;
    private String outgoingEmployeeName;
    private Long incomingShiftId;
    private String incomingEmployeeName;

    // Cash
    private BigDecimal expectedCash;
    private BigDecimal countedCash;
    private BigDecimal cashVariance;

    // Open tables
    private List<OpenTableInfo> openTables;
    private int openTableCount;

    // Pending orders
    private List<PendingOrderInfo> pendingOrders;
    private int pendingOrderCount;

    // Notes
    private String handoverNotes;

    private boolean completed;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenTableInfo {
        private Long tableId;
        private String tableNumber;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PendingOrderInfo {
        private Long orderId;
        private String orderNumber;
        private String status;
    }
}
