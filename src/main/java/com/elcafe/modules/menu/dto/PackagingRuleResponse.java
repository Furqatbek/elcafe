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
    private Long packagingProductId;
    private String packagingProductName;
    private BigDecimal packagingProductPrice;
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
        if (rule.getPackagingProduct() != null) {
            builder.packagingProductId(rule.getPackagingProduct().getId())
                    .packagingProductName(rule.getPackagingProduct().getName())
                    .packagingProductPrice(rule.getPackagingProduct().getPrice());
        }

        return builder.build();
    }
}
