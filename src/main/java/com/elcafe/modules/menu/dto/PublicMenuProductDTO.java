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
 * DTO for public menu product
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicMenuProductDTO {
    private Long id;
    private String name;
    private String description;
    private String imageUrl;
    private BigDecimal price;
    private BigDecimal priceWithMargin;
    private ItemType itemType;
    private Integer sortOrder;
    private ProductStatus status;
    private Boolean inStock;
    private Boolean featured;
    private Boolean hasVariants;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
