package com.elcafe.bff.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * BFF DTO for customer-facing menu.
 * Optimized for mobile app and online ordering.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerMenuDTO {

    private RestaurantInfoDTO restaurant;
    private List<MenuCategoryDTO> categories;
    private List<FeaturedProductDTO> featured;
    private List<PromotionBannerDTO> promotions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RestaurantInfoDTO {
        private Long id;
        private String name;
        private String logoUrl;
        private String coverImageUrl;
        private String description;
        private String cuisineType;
        private Double rating;
        private Integer reviewCount;
        private String address;
        private Boolean isOpen;
        private String openingHours;
        private Integer estimatedDeliveryMinutes;
        private BigDecimal minimumOrderAmount;
        private BigDecimal deliveryFee;
        private List<String> paymentMethods;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuCategoryDTO {
        private Long id;
        private String name;
        private String description;
        private String imageUrl;
        private List<MenuProductDTO> products;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MenuProductDTO {
        private Long id;
        private String name;
        private String description;
        private String imageUrl;
        private BigDecimal price;
        private BigDecimal originalPrice; // for showing discounts
        private Boolean isOnSale;
        private String saleLabel;
        private Boolean isAvailable;
        private Boolean isPopular;
        private Boolean isNew;
        private List<String> dietaryTags; // VEGETARIAN, VEGAN, GLUTEN_FREE, etc.
        private List<String> allergens;
        private Integer calories;
        private String preparationTime;
        private List<ProductVariantDTO> variants;
        private List<ProductAddOnGroupDTO> addOnGroups;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductVariantDTO {
        private Long id;
        private String name;
        private BigDecimal price;
        private Boolean isAvailable;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductAddOnGroupDTO {
        private Long id;
        private String name;
        private String description;
        private Boolean isRequired;
        private Integer minSelection;
        private Integer maxSelection;
        private List<ProductAddOnDTO> options;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductAddOnDTO {
        private Long id;
        private String name;
        private BigDecimal price;
        private Boolean isAvailable;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeaturedProductDTO {
        private Long productId;
        private String name;
        private String imageUrl;
        private BigDecimal price;
        private String tagline;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PromotionBannerDTO {
        private Long id;
        private String title;
        private String description;
        private String imageUrl;
        private String actionUrl;
        private String promoCode;
    }
}
