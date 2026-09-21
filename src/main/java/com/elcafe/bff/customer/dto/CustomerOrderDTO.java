package com.elcafe.bff.customer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * BFF DTO for customer order view.
 * Provides simplified order information for customer-facing apps.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerOrderDTO {

    private Long id;
    private String orderNumber;
    private String status;
    private String statusDisplayText;
    private String statusDescription;
    private Integer statusStep; // 1-5 for progress indicator

    private List<OrderItemDTO> items;
    private OrderPricingDTO pricing;
    private DeliveryInfoDTO delivery;

    private LocalDateTime placedAt;
    private LocalDateTime estimatedReadyTime;
    private LocalDateTime estimatedDeliveryTime;

    private Boolean canCancel;
    private Boolean canModify;
    private Boolean canReorder;
    private Boolean canRate;

    private String restaurantName;
    private String restaurantPhone;
    private String restaurantLogoUrl;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemDTO {
        private String productName;
        private String variantName;
        private Integer quantity;
        private BigDecimal price;
        private String imageUrl;
        private List<String> addOns;
        private String specialInstructions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderPricingDTO {
        private BigDecimal subtotal;
        private BigDecimal discount;
        private String discountDescription;
        private BigDecimal deliveryFee;
        private BigDecimal serviceFee;
        private BigDecimal tax;
        private BigDecimal tip;
        private BigDecimal total;
        private String currency;
        private String currencySymbol;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryInfoDTO {
        private String deliveryType; // DELIVERY, PICKUP, DINE_IN
        private String address;
        private String instructions;
        private String courierName;
        private String courierPhone;
        private String courierPhotoUrl;
        private TrackingInfoDTO tracking;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackingInfoDTO {
        private Double courierLatitude;
        private Double courierLongitude;
        private Double restaurantLatitude;
        private Double restaurantLongitude;
        private Double customerLatitude;
        private Double customerLongitude;
        private LocalDateTime lastUpdated;
    }
}
