package com.elcafe.modules.financial.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderItemResponse {

    private Long id;
    private Long purchaseOrderId;
    private Long ingredientId;
    private String ingredientName;
    private String itemName;
    private String description;
    private String sku;
    private BigDecimal quantity;
    private String unit;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private BigDecimal receivedQuantity;
    private Boolean fullyReceived;
    private String notes;
}
