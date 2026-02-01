package com.elcafe.bff.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * BFF DTO for POS Menu view.
 * Optimized for fast product lookup and order creation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class POSMenuDTO {

    private Long restaurantId;
    private List<CategoryDTO> categories;
    private List<QuickAccessProductDTO> quickAccessProducts;
    private Long menuVersion;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryDTO {
        private Long id;
        private String name;
        private String imageUrl;
        private Integer displayOrder;
        private Boolean isActive;
        private List<ProductDTO> products;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductDTO {
        private Long id;
        private String name;
        private String shortDescription;
        private String imageUrl;
        private BigDecimal basePrice;
        private Boolean hasVariants;
        private Boolean hasAddOns;
        private Boolean isAvailable;
        private String unavailableReason;
        private Integer stockLevel; // null if not tracked
        private List<VariantDTO> variants;
        private List<AddOnGroupDTO> addOnGroups;
        private List<String> dietaryTags;
        private List<String> allergens;
        private Integer displayOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VariantDTO {
        private Long id;
        private String name;
        private BigDecimal price;
        private Boolean isAvailable;
        private String sku;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddOnGroupDTO {
        private Long id;
        private String name;
        private Integer minSelection;
        private Integer maxSelection;
        private Boolean isRequired;
        private List<AddOnOptionDTO> options;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddOnOptionDTO {
        private Long id;
        private String name;
        private BigDecimal price;
        private Boolean isAvailable;
        private Boolean isDefault;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuickAccessProductDTO {
        private Long productId;
        private String name;
        private String imageUrl;
        private BigDecimal price;
        private String buttonColor;
        private Integer position;
    }
}
