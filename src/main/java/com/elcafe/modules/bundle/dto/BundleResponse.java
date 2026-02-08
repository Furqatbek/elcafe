package com.elcafe.modules.bundle.dto;

import com.elcafe.modules.bundle.entity.Bundle;
import com.elcafe.modules.bundle.entity.BundleItem;
import com.elcafe.modules.bundle.entity.BundleOption;
import com.elcafe.modules.bundle.entity.BundleOptionGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BundleResponse {

    private Long id;
    private Long restaurantId;
    private String restaurantName;
    private String name;
    private String description;
    private String imageUrl;
    private BigDecimal bundlePrice;
    private BigDecimal originalPrice;
    private BigDecimal savingsAmount;
    private BigDecimal savingsPercent;
    private Boolean active;
    private Boolean currentlyAvailable;
    private String availableFrom;
    private String availableUntil;
    private String availableDays;
    private Integer maxPerOrder;
    private Integer displayOrder;
    private List<BundleItemResponse> items;
    private List<OptionGroupResponse> optionGroups;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BundleItemResponse {
        private Long id;
        private Long productId;
        private String productName;
        private String productImage;
        private BigDecimal productPrice;
        private Integer quantity;
        private Boolean isRequired;
        private Boolean isDefault;
        private Integer displayOrder;

        public static BundleItemResponse from(BundleItem item) {
            return BundleItemResponse.builder()
                    .id(item.getId())
                    .productId(item.getProduct() != null ? item.getProduct().getId() : null)
                    .productName(item.getProduct() != null ? item.getProduct().getName() : null)
                    .productImage(item.getProduct() != null ? item.getProduct().getImageUrl() : null)
                    .productPrice(item.getProduct() != null ? item.getProduct().getPrice() : null)
                    .quantity(item.getQuantity())
                    .isRequired(item.getIsRequired())
                    .isDefault(item.getIsDefault())
                    .displayOrder(item.getDisplayOrder())
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionGroupResponse {
        private Long id;
        private String name;
        private String description;
        private Integer minSelections;
        private Integer maxSelections;
        private Boolean isRequired;
        private Integer displayOrder;
        private List<OptionResponse> options;

        public static OptionGroupResponse from(BundleOptionGroup group) {
            return OptionGroupResponse.builder()
                    .id(group.getId())
                    .name(group.getName())
                    .description(group.getDescription())
                    .minSelections(group.getMinSelections())
                    .maxSelections(group.getMaxSelections())
                    .isRequired(group.getIsRequired())
                    .displayOrder(group.getDisplayOrder())
                    .options(group.getOptions() != null ?
                            group.getOptions().stream()
                                    .map(OptionResponse::from)
                                    .collect(Collectors.toList()) : null)
                    .build();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionResponse {
        private Long id;
        private Long productId;
        private String productName;
        private String productImage;
        private BigDecimal productPrice;
        private BigDecimal priceAdjustment;
        private Boolean isDefault;
        private Integer displayOrder;

        public static OptionResponse from(BundleOption option) {
            return OptionResponse.builder()
                    .id(option.getId())
                    .productId(option.getProduct() != null ? option.getProduct().getId() : null)
                    .productName(option.getProduct() != null ? option.getProduct().getName() : null)
                    .productImage(option.getProduct() != null ? option.getProduct().getImageUrl() : null)
                    .productPrice(option.getProduct() != null ? option.getProduct().getPrice() : null)
                    .priceAdjustment(option.getPriceAdjustment())
                    .isDefault(option.getIsDefault())
                    .displayOrder(option.getDisplayOrder())
                    .build();
        }
    }

    public static BundleResponse from(Bundle bundle) {
        BigDecimal originalPrice = calculateOriginalPrice(bundle);
        BigDecimal savingsAmount = calculateSavingsAmount(bundle, originalPrice);
        BigDecimal savingsPercent = calculateSavingsPercent(bundle, originalPrice, savingsAmount);

        return BundleResponse.builder()
                .id(bundle.getId())
                .restaurantId(bundle.getRestaurant() != null ? bundle.getRestaurant().getId() : null)
                .restaurantName(bundle.getRestaurant() != null ? bundle.getRestaurant().getName() : null)
                .name(bundle.getName())
                .description(bundle.getDescription())
                .imageUrl(bundle.getImageUrl())
                .bundlePrice(bundle.getBundlePrice())
                .originalPrice(originalPrice)
                .savingsAmount(savingsAmount)
                .savingsPercent(savingsPercent)
                .active(bundle.getActive())
                .currentlyAvailable(bundle.isCurrentlyAvailable())
                .availableFrom(bundle.getAvailableFrom() != null ? bundle.getAvailableFrom().toString() : null)
                .availableUntil(bundle.getAvailableUntil() != null ? bundle.getAvailableUntil().toString() : null)
                .availableDays(bundle.getAvailableDays())
                .maxPerOrder(bundle.getMaxPerOrder())
                .displayOrder(bundle.getDisplayOrder())
                .items(bundle.getItems() != null ?
                        bundle.getItems().stream()
                                .map(BundleItemResponse::from)
                                .collect(Collectors.toList()) : null)
                .optionGroups(bundle.getOptionGroups() != null ?
                        bundle.getOptionGroups().stream()
                                .map(OptionGroupResponse::from)
                                .collect(Collectors.toList()) : null)
                .createdAt(bundle.getCreatedAt())
                .updatedAt(bundle.getUpdatedAt())
                .build();
    }

    /**
     * Calculate savings amount
     */
    private static BigDecimal calculateSavingsAmount(Bundle bundle, BigDecimal originalPrice) {
        if (bundle.getSavingsAmount() != null) {
            return bundle.getSavingsAmount();
        }
        if (originalPrice != null && bundle.getBundlePrice() != null) {
            BigDecimal savings = originalPrice.subtract(bundle.getBundlePrice());
            return savings.compareTo(BigDecimal.ZERO) > 0 ? savings : null;
        }
        return null;
    }

    /**
     * Calculate savings percent
     */
    private static BigDecimal calculateSavingsPercent(Bundle bundle, BigDecimal originalPrice, BigDecimal savingsAmount) {
        if (bundle.getSavingsPercent() != null) {
            return bundle.getSavingsPercent();
        }
        if (savingsAmount != null && originalPrice != null && originalPrice.compareTo(BigDecimal.ZERO) > 0) {
            return savingsAmount
                    .multiply(BigDecimal.valueOf(100))
                    .divide(originalPrice, 0, java.math.RoundingMode.HALF_UP);
        }
        return null;
    }

    /**
     * Calculate original price based on individual item prices.
     * Falls back to bundle items if originalPrice is not stored.
     */
    private static BigDecimal calculateOriginalPrice(Bundle bundle) {
        if (bundle.getOriginalPrice() != null) {
            return bundle.getOriginalPrice();
        }

        BigDecimal total = BigDecimal.ZERO;

        // Add required items
        if (bundle.getItems() != null) {
            for (BundleItem item : bundle.getItems()) {
                if (item.getProduct() != null && item.getProduct().getPrice() != null) {
                    int quantity = item.getQuantity() != null ? item.getQuantity() : 1;
                    total = total.add(item.getProduct().getPrice()
                            .multiply(BigDecimal.valueOf(quantity)));
                }
            }
        }

        // Add default options from option groups
        if (bundle.getOptionGroups() != null) {
            for (BundleOptionGroup group : bundle.getOptionGroups()) {
                if (group.getOptions() != null) {
                    for (BundleOption option : group.getOptions()) {
                        if (Boolean.TRUE.equals(option.getIsDefault()) &&
                            option.getProduct() != null &&
                            option.getProduct().getPrice() != null) {
                            total = total.add(option.getProduct().getPrice());
                            break; // Only count one default per group
                        }
                    }
                }
            }
        }

        return total.compareTo(BigDecimal.ZERO) > 0 ? total : null;
    }
}
