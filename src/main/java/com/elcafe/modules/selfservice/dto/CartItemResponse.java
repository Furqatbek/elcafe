package com.elcafe.modules.selfservice.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class CartItemResponse {
    private Long id;
    private Long productId;
    private String productName;
    private String productNameUz;
    private String productNameRu;
    private String imageUrl;
    private Long variantId;
    private String variantName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private String specialInstructions;
    private List<ModifierResponse> modifiers;

    @Data
    @Builder
    public static class ModifierResponse {
        private Long id;
        private Long linkedItemId;
        private String name;
        private Integer quantity;
        private BigDecimal price;
    }
}
