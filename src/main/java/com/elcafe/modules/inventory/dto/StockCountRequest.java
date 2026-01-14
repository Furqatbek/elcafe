package com.elcafe.modules.inventory.dto;

import com.elcafe.modules.inventory.entity.StockCount;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockCountRequest {

    private Long restaurantId;
    private StockCount.CountType countType;
    private LocalDate scheduledDate;
    private String notes;
    private String initiatedBy;

    // For CYCLE or SPOT_CHECK counts - specific ingredient IDs to count
    private List<Long> ingredientIds;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecordCountRequest {
        private Long itemId;
        private java.math.BigDecimal countedQuantity;
        private String countedBy;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VarianceReasonRequest {
        private Long itemId;
        private com.elcafe.modules.inventory.entity.StockCountItem.VarianceReason varianceReason;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApproveRequest {
        private String approvedBy;
        private boolean adjustInventory;
        private String notes;
    }
}
