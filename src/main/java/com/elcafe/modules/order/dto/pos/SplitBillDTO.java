package com.elcafe.modules.order.dto.pos;

import jakarta.validation.constraints.NotNull;
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
public class SplitBillDTO {

    @NotNull(message = "Split mode is required")
    private SplitMode mode;

    // For ITEMS mode
    private List<ItemSplit> itemSplits;

    // For EVEN mode
    private Integer numPeople;

    // For AMOUNT mode
    private List<AmountSplit> amountSplits;

    public enum SplitMode {
        ITEMS,
        EVEN,
        AMOUNT
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemSplit {
        private Integer personNumber;
        private List<Long> itemIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AmountSplit {
        private Integer personNumber;
        private BigDecimal amount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitItemInfo {
        private Long itemId;
        private String productName;
        private Integer quantity;
        private BigDecimal price;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillSplit {
        private Integer personNumber;
        private BigDecimal amount;
        private List<SplitItemInfo> items;
        private Boolean paid;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SplitBillResponse {
        private Long orderId;
        private String orderNumber;
        private SplitMode mode;
        private BigDecimal originalTotal;
        private List<BillSplit> splits;
    }
}
