package com.elcafe.modules.order.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Event sourcing entity for order financial data.
 * Tracks all financial state changes to allow reconstruction of order state at any point in time.
 * This is critical for POS systems for auditing, dispute resolution, and compliance.
 */
@Entity
@Table(name = "order_financial_events", indexes = {
        @Index(name = "idx_financial_event_order", columnList = "order_id"),
        @Index(name = "idx_financial_event_type", columnList = "event_type"),
        @Index(name = "idx_financial_event_created", columnList = "created_at"),
        @Index(name = "idx_financial_event_sequence", columnList = "order_id, sequence_number")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderFinancialEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_number", nullable = false, length = 50)
    private String orderNumber;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EventType eventType;

    // Financial snapshot at this point
    @Column(name = "subtotal", precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "tax", precision = 10, scale = 2)
    private BigDecimal tax;

    @Column(name = "delivery_fee", precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    @Column(name = "service_fee", precision = 10, scale = 2)
    private BigDecimal serviceFee;

    @Column(name = "service_fee_percent", precision = 5, scale = 2)
    private BigDecimal serviceFeePercent;

    @Column(name = "entry_fee", precision = 10, scale = 2)
    private BigDecimal entryFee;

    @Column(name = "discount", precision = 10, scale = 2)
    private BigDecimal discount;

    @Column(name = "discount_type", length = 50)
    private String discountType;

    @Column(name = "total", precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "tip_amount", precision = 10, scale = 2)
    private BigDecimal tipAmount;

    @Column(name = "grand_total", precision = 10, scale = 2)
    private BigDecimal grandTotal;

    @Column(name = "total_paid", precision = 10, scale = 2)
    private BigDecimal totalPaid;

    @Column(name = "refunded_amount", precision = 10, scale = 2)
    private BigDecimal refundedAmount;

    // Event-specific data
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "payment_method", length = 50)
    private String paymentMethod;

    @Column(name = "payment_amount", precision = 10, scale = 2)
    private BigDecimal paymentAmount;

    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "item_name", length = 255)
    private String itemName;

    @Column(name = "item_quantity")
    private Integer itemQuantity;

    @Column(name = "item_price", precision = 10, scale = 2)
    private BigDecimal itemPrice;

    @Column(name = "promotion_id")
    private Long promotionId;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    // Metadata
    @Column(name = "performed_by", length = 100)
    private String performedBy;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "terminal_id", length = 50)
    private String terminalId;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum EventType {
        // Order lifecycle
        ORDER_CREATED,
        ORDER_UPDATED,
        ORDER_CANCELLED,
        ORDER_VOIDED,

        // Item events
        ITEM_ADDED,
        ITEM_REMOVED,
        ITEM_QUANTITY_CHANGED,
        ITEM_PRICE_MODIFIED,

        // Payment events
        PAYMENT_RECEIVED,
        PAYMENT_FAILED,
        PARTIAL_PAYMENT,
        PAYMENT_VOIDED,
        REFUND_ISSUED,
        PARTIAL_REFUND,
        TIP_ADDED,

        // Fee events
        SERVICE_FEE_APPLIED,
        SERVICE_FEE_REMOVED,
        ENTRY_FEE_APPLIED,
        ENTRY_FEE_REMOVED,
        DELIVERY_FEE_APPLIED,
        DELIVERY_FEE_CHANGED,

        // Discount events
        DISCOUNT_APPLIED,
        DISCOUNT_REMOVED,
        COUPON_APPLIED,
        COUPON_REMOVED,
        PROMOTION_APPLIED,
        HAPPY_HOUR_APPLIED,

        // Tax events
        TAX_CALCULATED,
        TAX_EXEMPT_APPLIED,

        // Adjustments
        MANUAL_ADJUSTMENT,
        PRICE_OVERRIDE,
        COMP_APPLIED,

        // Split bill
        BILL_SPLIT_INITIATED,
        SPLIT_PAYMENT_RECEIVED
    }

    /**
     * Create a snapshot of order financial state.
     */
    public static OrderFinancialEvent fromOrder(Order order, EventType eventType, String performedBy) {
        return OrderFinancialEvent.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .eventType(eventType)
                .subtotal(order.getSubtotal())
                .tax(order.getTax())
                .deliveryFee(order.getDeliveryFee())
                .serviceFee(order.getServiceFee())
                .serviceFeePercent(order.getServiceFeePercent())
                .entryFee(order.getEntryFee())
                .discount(order.getDiscount())
                .discountType(order.getDiscountType())
                .total(order.getTotal())
                .tipAmount(order.getTipAmount())
                .grandTotal(order.getGrandTotal())
                .totalPaid(order.getTotalPaid())
                .performedBy(performedBy)
                .build();
    }
}
