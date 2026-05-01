package com.elcafe.modules.menu.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePackagingRuleRequest {

    @NotNull(message = "Restaurant ID is required")
    private Long restaurantId;

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Packaging ingredient ID is required")
    private Long packagingIngredientId;

    @Builder.Default
    private String orderTypes = "DELIVERY,TAKEAWAY";

    @Builder.Default
    private String quantityMode = "PER_ITEM";

    @Builder.Default
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer autoAddQuantity = 1;

    @Builder.Default
    private Boolean chargeToCustomer = false;
}
