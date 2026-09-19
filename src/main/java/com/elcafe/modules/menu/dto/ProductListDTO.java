package com.elcafe.modules.menu.dto;

import com.elcafe.modules.menu.enums.ItemType;
import com.elcafe.modules.menu.enums.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for product listing with category information
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductListDTO {
    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private BigDecimal price;
    private BigDecimal priceWithMargin;
    private BigDecimal costPrice;
    private BigDecimal marginPercentage;
    private ItemType itemType;
    private Integer sortOrder;
    private ProductStatus status;
    private Boolean inStock;
    private Boolean featured;
    private Boolean hasVariants;

    // Category information
    private Long categoryId;
    private String categoryName;

    // For frontend compatibility
    private Boolean available; // Maps to inStock
    private Boolean isFeatured; // Maps to featured

    /**
     * False when a non-optional ingredient is short. Deliberately NOT folded into {@code available}:
     * our own screens and the customer app keep selling on the manual switch, and this is here so a
     * manager can see why an aggregator has stopped showing a dish whose switch is still on.
     */
    private Boolean recipeAvailable;

    // Weight-based selling
    private Boolean isSoldByWeight;
    private String weightUnit;
    private BigDecimal minWeight;
    private BigDecimal maxWeight;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
