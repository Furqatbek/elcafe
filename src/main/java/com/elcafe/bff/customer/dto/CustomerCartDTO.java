package com.elcafe.bff.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * BFF DTO for customer shopping cart.
 * Provides real-time pricing and availability updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerCartDTO {

    private String cartId;
    private Long restaurantId;
    private String restaurantName;
    private List<CartItemDTO> items;
    private CartPricingDTO pricing;
    private List<ApplicablePromotionDTO> applicablePromotions;
    private String appliedPromoCode;
    private List<CartValidationIssueDTO> validationIssues;
    private Boolean canCheckout;
    private String checkoutBlockedReason;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemDTO {
        private String cartItemId;
        private Long productId;
        private Long variantId;
        private String productName;
        private String variantName;
        private String imageUrl;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        private List<CartAddOnDTO> addOns;
        private String specialInstructions;
        private Boolean isAvailable;
        private String unavailableReason;
        private Integer maxQuantityAvailable;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartAddOnDTO {
        private Long addOnId;
        private String name;
        private BigDecimal price;
        private Integer quantity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartPricingDTO {
        private BigDecimal subtotal;
        private BigDecimal itemDiscount;
        private BigDecimal promoDiscount;
        private BigDecimal estimatedTax;
        private BigDecimal estimatedDeliveryFee;
        private BigDecimal estimatedServiceFee;
        private BigDecimal estimatedTotal;
        private BigDecimal minimumOrderAmount;
        private BigDecimal amountToMinimum;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApplicablePromotionDTO {
        private String promoCode;
        private String title;
        private String description;
        private BigDecimal estimatedSavings;
        private Boolean isAutoApply;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartValidationIssueDTO {
        private String issueType; // ITEM_UNAVAILABLE, BELOW_MINIMUM, RESTAURANT_CLOSED
        private String severity; // ERROR, WARNING
        private String message;
        private String cartItemId;
        private String suggestedAction;
    }
}
