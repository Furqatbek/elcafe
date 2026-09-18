package com.elcafe.modules.partner.dto;

import com.elcafe.modules.order.enums.OrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * An order handed to us by a delivery aggregator.
 *
 * <p>Distinct from {@code CreateOrderRequest} on purpose. That one is consumer-shaped: it carries a
 * coupon code, a wallet payment method and a "find or create this customer" block, all of which assume
 * a person signed in to our own app. A partner is a machine acting for a customer we have never met and
 * whose loyalty account is not ours to touch, so it gets a contract with none of that — and two fields
 * a consumer never needs: {@link #externalOrderId} for correlation and deduplication, and
 * {@link #expectedTotal} so a disagreement about price is caught before the kitchen starts cooking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerOrderRequest {

    @NotNull(message = "Restaurant ID is required")
    private Long restaurantId;

    /**
     * The partner's own identifier for this order. Unique per partner and permanent: it is both the
     * correlation key for support and the deduplication key, so a retried push produces the same order
     * rather than a second one.
     */
    @NotBlank(message = "External order ID is required")
    @Size(max = 190, message = "External order ID must not exceed 190 characters")
    private String externalOrderId;

    /**
     * DELIVERY or TAKEAWAY. DINE_IN is accepted by the type but meaningless from an aggregator, and the
     * delivery fee is charged for DELIVERY only.
     */
    @NotNull(message = "Order type is required")
    private OrderType orderType;

    @Valid
    @NotEmpty(message = "Order must contain at least one item")
    private List<Item> items;

    @Valid
    private Customer customer;

    @Valid
    private Delivery delivery;

    /**
     * How the customer paid. PREPAID means the partner already collected the money and the order
     * arrives settled; CASH means the venue or courier collects on handover.
     */
    @NotNull(message = "Payment method is required")
    private PaymentMode paymentMode;

    @Size(max = 1000, message = "Notes must not exceed 1000 characters")
    private String notes;

    private LocalDateTime scheduledFor;

    /**
     * What the partner charged the customer, for the goods and the delivery fee together. Optional but
     * strongly encouraged: when present we refuse the order if our own arithmetic disagrees, which
     * catches a stale cached menu before it becomes a customer paying one price for food priced
     * differently. Omit it and the order is accepted at our price, whatever the partner quoted.
     */
    @DecimalMin(value = "0.0", message = "Expected total cannot be negative")
    private BigDecimal expectedTotal;

    public enum PaymentMode {
        /** Already collected by the partner. The order is created settled. */
        PREPAID,
        /** Collected on handover. The order is created awaiting payment. */
        CASH
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {

        @NotNull(message = "Product ID is required")
        private Long productId;

        /** Required when the product has variants; the variant's price then replaces the base price. */
        private Long variantId;

        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 100, message = "Quantity must not exceed 100")
        private Integer quantity;

        /** Add-on ids from the product's own add-on groups. Each is priced and added to the line. */
        @Builder.Default
        private List<Long> addOnIds = new ArrayList<>();

        @Size(max = 500, message = "Special instructions must not exceed 500 characters")
        private String specialInstructions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Customer {

        @Size(max = 100, message = "Name must not exceed 100 characters")
        private String name;

        /**
         * Reaching the customer matters more than identifying them. We deliberately do NOT look this
         * up against our own customers or attach loyalty: the person is the partner's customer, and
         * silently merging them into a local profile would mix two businesses' relationships.
         */
        @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid phone number format")
        private String phone;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Delivery {

        @Size(max = 500, message = "Address must not exceed 500 characters")
        private String address;

        @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
        private BigDecimal latitude;

        @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
        private BigDecimal longitude;

        @Size(max = 500, message = "Delivery instructions must not exceed 500 characters")
        private String instructions;
    }
}
