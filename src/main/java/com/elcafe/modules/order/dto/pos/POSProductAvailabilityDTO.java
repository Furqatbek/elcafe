package com.elcafe.modules.order.dto.pos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class POSProductAvailabilityDTO {

    private Long productId;
    private String productName;
    private Boolean available;
    private Integer maxQuantityAvailable;
    private String stockStatus; // AVAILABLE, LOW_STOCK, OUT_OF_STOCK
    private List<IngredientAvailability> ingredientDetails;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngredientAvailability {
        private Long ingredientId;
        private String ingredientName;
        private String unit;
        private Double currentStock;
        private Double requiredPerUnit;
        private Integer maxServings;
        private Boolean sufficient;
    }
}
