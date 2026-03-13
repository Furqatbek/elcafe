package com.elcafe.modules.order.dto.pos;

import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class POSOrderResponse {

    private Long id;
    private Long restaurantId;
    private String orderNumber;
    private OrderStatus status;
    private String orderType;
    private String customerName;
    private String customerPhone;
    private List<OrderItemResponse> items;
    private BigDecimal subtotal;
    private BigDecimal tax;
    private BigDecimal deliveryFee;
    private BigDecimal serviceFeePercent;
    private BigDecimal serviceFee;
    private BigDecimal entryFee;
    private BigDecimal total;
    private String paymentMethod;
    private PaymentStatus paymentStatus;
    private boolean fullyPaid;
    private String orderNotes;
    private OffsetDateTime createdAt;
    private OffsetDateTime estimatedDeliveryTime;
    private DeliveryAddressResponse deliveryAddress;
    private DineInInfoResponse dineInInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemResponse {
        private Long id;
        private String productName;
        private String variantName;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal price; // alias for unitPrice (backward compatibility)
        private BigDecimal totalPrice;
        private List<String> modifiers;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryAddressResponse {
        private String street;
        private String city;
        private String state;
        private String zipCode;
        private String deliveryInstructions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DineInInfoResponse {
        private String tableNumber;
        private List<Long> tableIds;
        private Integer guestCount;
    }
}
