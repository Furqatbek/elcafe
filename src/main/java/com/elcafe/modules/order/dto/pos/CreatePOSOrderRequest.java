package com.elcafe.modules.order.dto.pos;

import com.elcafe.modules.order.enums.OrderSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
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
public class CreatePOSOrderRequest {

    @NotNull(message = "Restaurant ID is required")
    private Long restaurantId;

    @NotNull(message = "Order type is required")
    private OrderType orderType; // DELIVERY, TAKEAWAY, DINE_IN

    @NotNull(message = "Order source is required")
    @Builder.Default
    private OrderSource orderSource = OrderSource.WALK_IN;

    @Valid
    @NotNull(message = "Customer information is required")
    private CustomerInfo customerInfo;

    @Valid
    @NotEmpty(message = "Order must contain at least one item")
    private List<OrderItemRequest> items;

    // For DELIVERY orders
    @Valid
    private DeliveryInfo deliveryInfo;

    // For DINE_IN orders
    private DineInInfo dineInInfo;

    @Size(max = 1000, message = "Order notes must not exceed 1000 characters")
    private String orderNotes;

    // Payment method is optional - orders can be created without payment (pay later)
    private String paymentMethod; // CASH, CARD, MOBILE (optional)

    @NotNull(message = "Subtotal is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Subtotal must be greater than 0")
    private BigDecimal subtotal;

    @NotNull(message = "Tax is required")
    @DecimalMin(value = "0.0", message = "Tax must be 0 or greater")
    private BigDecimal tax;

    @DecimalMin(value = "0.0", message = "Delivery fee must be 0 or greater")
    @Builder.Default
    private BigDecimal deliveryFee = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", message = "Service fee percent must be 0 or greater")
    @DecimalMax(value = "100.0", message = "Service fee percent must not exceed 100")
    @Builder.Default
    private BigDecimal serviceFeePercent = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", message = "Service fee must be 0 or greater")
    @Builder.Default
    private BigDecimal serviceFee = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", message = "Entry fee must be 0 or greater")
    @Builder.Default
    private BigDecimal entryFee = BigDecimal.ZERO;

    @NotNull(message = "Total is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Total must be greater than 0")
    private BigDecimal total;

    // Cash payment fields
    private BigDecimal amountTendered;
    private BigDecimal changeDue;

    public enum OrderType {
        DELIVERY,
        TAKEAWAY,
        DINE_IN
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerInfo {

        // Name and phone are optional for dine-in orders
        @Size(max = 200, message = "Name must not exceed 200 characters")
        private String name;

        @Size(max = 50, message = "Phone must not exceed 50 characters")
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

        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
        private BigDecimal price;

        private List<ModifierInfo> modifiers;

        @Size(max = 500, message = "Notes must not exceed 500 characters")
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModifierInfo {

        @NotBlank(message = "Modifier name is required")
        private String name;

        @NotNull(message = "Modifier price is required")
        @DecimalMin(value = "0.0", message = "Price must be 0 or greater")
        private BigDecimal price;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryInfo {

        @NotBlank(message = "Street address is required")
        @Size(max = 500, message = "Address must not exceed 500 characters")
        private String street;

        @NotBlank(message = "City is required")
        @Size(max = 100, message = "City must not exceed 100 characters")
        private String city;

        @Size(max = 100, message = "State must not exceed 100 characters")
        private String state;

        @Size(max = 20, message = "ZIP code must not exceed 20 characters")
        private String zipCode;

        @Size(max = 500, message = "Delivery instructions must not exceed 500 characters")
        private String deliveryInstructions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DineInInfo {

        @NotBlank(message = "Table number is required")
        @Size(max = 50, message = "Table number must not exceed 50 characters")
        private String tableNumber;

        // Array of table IDs for multi-table orders
        private List<Long> tableIds;

        @Min(value = 1, message = "Guest count must be at least 1")
        @Max(value = 100, message = "Guest count must not exceed 100")
        private Integer guestCount;
    }
}
