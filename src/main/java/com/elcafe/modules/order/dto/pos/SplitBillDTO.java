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

    // For ITEMS mode - list of item assignments to persons
    private List<ItemSplit> itemSplits;

    // For EVEN mode - number of people to split between
    private Integer numPeople;

    // For AMOUNT mode - custom amount splits
    private List<AmountSplit> amountSplits;

    public enum SplitMode {
        ITEMS,    // Split by assigning items to different persons
        EVEN,     // Split evenly between N people
        AMOUNT    // Split by custom amounts
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

    // Response classes
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BillSplit {
        private Integer personNumber;
        private BigDecimal amount;
        private List<SplitItemInfo> items;
        private boolean paid;
        private String paymentMethod;
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
}
