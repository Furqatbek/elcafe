package com.elcafe.modules.order.dto.consumer;

import com.elcafe.modules.order.enums.OrderSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
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
public class CreateOrderRequest {

    @NotNull(message = "Restaurant ID is required")
    private Long restaurantId;

    @NotNull(message = "Order source is required")
    private OrderSource orderSource;

    @Size(max = 50, message = "Order type must not exceed 50 characters")
    private String orderType; // DELIVERY, PICKUP, DINE_IN

    @Valid
    @NotNull(message = "Customer information is required")
    private CustomerInfo customerInfo;

    @Valid
    @NotEmpty(message = "Order must contain at least one item")
    private List<OrderItemRequest> items;

    @Valid
    @NotNull(message = "Delivery information is required")
    private DeliveryInfoRequest deliveryInfo;

    @Size(max = 1000, message = "Customer notes must not exceed 1000 characters")
    private String customerNotes;

    private LocalDateTime scheduledFor;

    @NotNull(message = "Payment method is required")
    private String paymentMethod; // CASH, CARD, ONLINE

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerInfo {

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must not exceed 100 characters")
        private String firstName;

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must not exceed 100 characters")
        private String lastName;

        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
        private String phone;

        @Email(message = "Invalid email format")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        private String email;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemRequest {

        @NotNull(message = "Product ID is required")
        private Long productId;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 100, message = "Quantity must not exceed 100")
        private Integer quantity;

        @Size(max = 500, message = "Special instructions must not exceed 500 characters")
        private String specialInstructions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryInfoRequest {

        @NotBlank(message = "Delivery address is required")
        @Size(max = 500, message = "Address must not exceed 500 characters")
        private String address;

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must not exceed 100 characters")
        private String city;

        @Size(max = 100, message = "State must not exceed 100 characters")
        private String state;

        @Size(max = 20, message = "ZIP code must not exceed 20 characters")
        private String zipCode;

        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        private BigDecimal latitude;

        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        private BigDecimal longitude;

        @Size(max = 500, message = "Delivery instructions must not exceed 500 characters")
        private String deliveryInstructions;
    }
}
