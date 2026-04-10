package com.elcafe.modules.order.dto.pos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
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
public class ModifyOrderItemRequest {

    @NotNull(message = "Product ID is required")
    private Long productId;

    private Long variantId;

    private String variantName;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    private BigDecimal price;

    /**
     * For weight-based products: actual weight ordered (e.g., 0.75 for 750g).
     * When provided, totalPrice = price × weightAmount × quantity.
     */
    @DecimalMin(value = "0.0", inclusive = false, message = "Weight must be greater than 0")
    private BigDecimal weightAmount;

    /**
     * For portion-based products: multiplier on unit price.
     * 0.5 = half portion, 1.0 = full (default), 2.0 = double portion.
     */
    @DecimalMin(value = "0.0", inclusive = false, message = "Portion multiplier must be greater than 0")
    private BigDecimal portionMultiplier;

    private List<ModifierInfo> modifiers;

    private String notes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModifierInfo {
        /**
         * Reference to the AddOn entity for tracking and reporting.
         * Optional - can be null for custom modifiers.
         */
        private Long addOnId;

        private String name;
        private BigDecimal price;

        /**
         * Quantity of this modifier (default 1).
         */
        @Builder.Default
        private Integer quantity = 1;
    }
}
