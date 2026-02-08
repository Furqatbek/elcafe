package com.elcafe.modules.selfservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class AddToCartRequest {

    /**
     * Product ID - required unless this is a bundle item.
     */
    private Long productId;

    /**
     * Variant ID - optional, for products with variants.
     */
    private Long variantId;

    /**
     * Quantity must be between 1 and 99.
     */
    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    @Max(value = 99, message = "Quantity cannot exceed 99")
    private Integer quantity = 1;

    /**
     * Special instructions - limited to 500 characters.
     */
    @Size(max = 500, message = "Special instructions cannot exceed 500 characters")
    private String specialInstructions;

    /**
     * Modifiers/add-ons for this cart item.
     */
    @Valid
    @Size(max = 20, message = "Cannot have more than 20 modifiers per item")
    private List<ModifierRequest> modifiers;

    // Bundle support
    private Long bundleId;
    private Boolean isBundle = false;

    @Size(max = 50, message = "Cannot select more than 50 options")
    private List<Long> selectedOptionIds;

    /**
     * Validates that either productId or bundleId is provided.
     */
    @AssertTrue(message = "Either productId or bundleId must be provided")
    public boolean isValidItemType() {
        if (Boolean.TRUE.equals(isBundle)) {
            return bundleId != null;
        }
        return productId != null;
    }

    @Data
    public static class ModifierRequest {
        @NotNull(message = "Linked item ID is required")
        private Long linkedItemId;

        @NotNull(message = "Modifier quantity is required")
        @Min(value = 1, message = "Modifier quantity must be at least 1")
        @Max(value = 10, message = "Modifier quantity cannot exceed 10")
        private Integer quantity = 1;
    }
}
