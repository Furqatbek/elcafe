package com.elcafe.modules.selfservice.dto;

import lombok.Data;

import java.util.List;

@Data
public class AddToCartRequest {
    private Long productId;
    private Long variantId;
    private Integer quantity = 1;
    private String specialInstructions;
    private List<ModifierRequest> modifiers;

    // Bundle support
    private Long bundleId;
    private Boolean isBundle = false;
    private List<Long> selectedOptionIds;

    @Data
    public static class ModifierRequest {
        private Long linkedItemId;
        private Integer quantity = 1;
    }
}
