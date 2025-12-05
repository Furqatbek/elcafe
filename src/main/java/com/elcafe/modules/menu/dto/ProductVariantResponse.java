package com.elcafe.modules.menu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String name;
    private String description;
    private BigDecimal price;
    private Boolean inStock;
    private Integer sortOrder;
}
