package com.elcafe.modules.order.dto.consumer;

import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private Long id;
    private String orderNumber;
    private OrderStatus status;
    private OrderSource orderSource;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal tax;
    private BigDecimal discount;
    private BigDecimal total;
    private String customerNotes;
    private LocalDateTime scheduledFor;
    private LocalDateTime createdAt;
    private LocalDateTime estimatedDeliveryTime;

    private RestaurantInfo restaurant;
    private CustomerInfo customer;
    private List<OrderItemInfo> items;
    private DeliveryInfo deliveryInfo;
    private PaymentInfo paymentInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RestaurantInfo {
        private Long id;
        private String name;
        private String phone;
        private String address;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerInfo {
        private Long id;
        private String firstName;
        private String lastName;
        private String phone;
        private String email;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemInfo {
        private Long id;
        private String productName;
        private Integer quantity;
        private BigDecimal price;
        private BigDecimal total;
        private String specialInstructions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryInfo {
        private String address;
        private String city;
        private String state;
        private String zipCode;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private String deliveryInstructions;
        private String courierName;
        private String courierPhone;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentInfo {
        private String paymentMethod;
        private String paymentStatus;
        private BigDecimal amount;
    }
}
