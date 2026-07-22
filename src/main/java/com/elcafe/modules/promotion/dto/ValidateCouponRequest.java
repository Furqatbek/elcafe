package com.elcafe.modules.promotion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateCouponRequest {

    @NotBlank(message = "Coupon code is required")
    private String code;

    @NotNull(message = "Restaurant ID is required")
    private Long restaurantId;

    private Long customerId;

    @NotNull(message = "Order subtotal is required")
    private BigDecimal orderSubtotal;

    private String orderType; // DINE_IN, TAKEAWAY, DELIVERY

    private List<OrderItemInfo> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemInfo {
        private Long productId;
        private Long categoryId;
        private Integer quantity;
        private BigDecimal price;
    }
}
