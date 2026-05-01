package com.elcafe.modules.menu.dto;

import com.elcafe.modules.menu.entity.PackagingRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackagingRuleResponse {

    private Long id;
    private Long restaurantId;
    private Long productId;
    private String productName;
    private Long packagingIngredientId;
    private String packagingIngredientName;
    private String packagingIngredientUnit;
    private BigDecimal packagingIngredientCost;
    private String orderTypes;
    private String quantityMode;
    private Integer autoAddQuantity;
    private Boolean chargeToCustomer;
    private Boolean active;

    public static PackagingRuleResponse fromEntity(PackagingRule rule) {
        PackagingRuleResponseBuilder builder = PackagingRuleResponse.builder()
                .id(rule.getId())
                .restaurantId(rule.getRestaurant().getId())
                .orderTypes(rule.getOrderTypes())
                .quantityMode(rule.getQuantityMode().name())
                .autoAddQuantity(rule.getAutoAddQuantity())
                .chargeToCustomer(rule.getChargeToCustomer())
                .active(rule.getActive());

        if (rule.getProduct() != null) {
            builder.productId(rule.getProduct().getId())
                    .productName(rule.getProduct().getName());
        }
        if (rule.getPackagingIngredient() != null) {
            builder.packagingIngredientId(rule.getPackagingIngredient().getId())
                    .packagingIngredientName(rule.getPackagingIngredient().getName())
                    .packagingIngredientUnit(rule.getPackagingIngredient().getUnit())
                    .packagingIngredientCost(rule.getPackagingIngredient().getEffectiveCost());
        }

        return builder.build();
    }
}
