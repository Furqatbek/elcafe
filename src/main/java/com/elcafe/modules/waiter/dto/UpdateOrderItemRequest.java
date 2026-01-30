package com.elcafe.modules.waiter.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
public class UpdateOrderItemRequest {

    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    /**
     * @deprecated Use {@link #modifiers} instead for structured add-on data.
     * This field is kept for backward compatibility and will be removed in a future version.
     */
    @Deprecated
    private String addOns;

    /**
     * Structured add-on/modifier list with proper tracking.
     * Replaces the deprecated addOns string field.
     */
    private List<AddOnInfo> modifiers;

    @Size(max = 500, message = "Special instructions must not exceed 500 characters")
    private String specialInstructions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddOnInfo {
        /**
         * Reference to the AddOn entity for tracking and reporting.
         * Optional - can be null for custom modifiers.
         */
        private Long addOnId;

        private String name;

        @Builder.Default
        private BigDecimal price = BigDecimal.ZERO;

        @Builder.Default
        private Integer quantity = 1;
    }
}
