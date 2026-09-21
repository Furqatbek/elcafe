package com.elcafe.bff.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * BFF DTO for detailed POS Order view.
 * Includes all information needed to display and manage an order.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class POSOrderDetailDTO {

    private Long id;
    private String orderNumber;
    private String status;
    private String statusDisplayName;
    private String orderType;
    private String orderTypeDisplayName;

    // Customer information
    private CustomerInfo customer;

    // Order items with full details
    private List<OrderItemDetailDTO> items;

    // Pricing breakdown
    private PricingBreakdown pricing;

    // Payment information
    private List<PaymentInfo> payments;
    private BigDecimal outstandingBalance;
    private Boolean isPaid;

    // Delivery/Dine-in specific info
    private DeliveryInfo delivery;
    private DineInInfo dineIn;

    // Kitchen status
    private KitchenStatus kitchenStatus;

    // Audit trail
    private AuditInfo audit;

    // Actions available for this order
    private List<String> availableActions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerInfo {
        private Long customerId;
        private String name;
        private String phone;
        private String email;
        private Boolean isLoyaltyMember;
        private Integer loyaltyPoints;
        private String loyaltyTier;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemDetailDTO {
        private Long id;
        private Long productId;
        private String productName;
        private String variantName;
        private String sku;
        private Integer quantity;
        private BigDecimal unitPrice;
        private BigDecimal lineTotal;
        private BigDecimal discountAmount;
        private List<AddOnDTO> addOns;
        private List<ModifierDTO> modifiers;
        private String specialInstructions;
        private String kitchenStatus;
        private Boolean canModify;
        private Boolean canRemove;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddOnDTO {
        private Long id;
        private String name;
        private BigDecimal price;
        private Integer quantity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModifierDTO {
        private String groupName;
        private String optionName;
        private BigDecimal priceAdjustment;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PricingBreakdown {
        private BigDecimal subtotal;
        private BigDecimal itemDiscounts;
        private BigDecimal orderDiscount;
        private String discountDescription;
        private BigDecimal taxableAmount;
        private BigDecimal taxRate;
        private BigDecimal taxAmount;
        private BigDecimal serviceFeePercent;
        private BigDecimal serviceFeeAmount;
        private BigDecimal entryFee;
        private BigDecimal deliveryFee;
        private BigDecimal tipAmount;
        private BigDecimal grandTotal;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentInfo {
        private Long paymentId;
        private String paymentMethod;
        private String paymentMethodDisplayName;
        private BigDecimal amount;
        private String status;
        private String transactionId;
        private LocalDateTime paidAt;
        private Boolean canRefund;
        private BigDecimal refundedAmount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryInfo {
        private String street;
        private String apartment;
        private String city;
        private String state;
        private String zipCode;
        private String deliveryInstructions;
        private Double latitude;
        private Double longitude;
        private String courierName;
        private String courierPhone;
        private String deliveryStatus;
        private LocalDateTime estimatedDeliveryTime;
        private LocalDateTime actualDeliveryTime;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DineInInfo {
        private List<TableInfo> tables;
        private Integer guestCount;
        private String serverName;
        private LocalDateTime seatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableInfo {
        private Long tableId;
        private String tableNumber;
        private String section;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KitchenStatus {
        private String overallStatus;
        private LocalDateTime acceptedAt;
        private LocalDateTime preparingAt;
        private LocalDateTime readyAt;
        private List<KitchenItemStatus> itemStatuses;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KitchenItemStatus {
        private Long itemId;
        private String productName;
        private Integer quantity;
        private String station;
        private String status;
        private LocalDateTime statusUpdatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditInfo {
        private LocalDateTime createdAt;
        private String createdBy;
        private LocalDateTime lastModifiedAt;
        private String lastModifiedBy;
        private Integer version;
    }
}
