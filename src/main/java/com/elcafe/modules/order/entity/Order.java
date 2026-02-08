package com.elcafe.modules.order.entity;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.waiter.entity.Waiter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@jakarta.persistence.Table(name = "orders", indexes = {
        @Index(name = "idx_order_restaurant_status", columnList = "restaurant_id, status"),
        @Index(name = "idx_order_restaurant_created", columnList = "restaurant_id, created_at"),
        @Index(name = "idx_order_customer", columnList = "customer_id"),
        @Index(name = "idx_order_dining_table", columnList = "dining_table_id"),
        @Index(name = "idx_order_waiter", columnList = "waiter_id"),
        @Index(name = "idx_order_status", columnList = "status"),
        @Index(name = "idx_order_created_at", columnList = "created_at"),
        @Index(name = "idx_order_payment_intent", columnList = "payment_intent_id"),
        @Index(name = "idx_order_deleted_at", columnList = "deleted_at")
})
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@NamedEntityGraph(
    name = "Order.withItems",
    attributeNodes = {
        @NamedAttributeNode("items"),
        @NamedAttributeNode("diningTable"),
        @NamedAttributeNode("waiter"),
        @NamedAttributeNode(value = "orderTables", subgraph = "orderTables-subgraph")
    },
    subgraphs = {
        @NamedSubgraph(
            name = "orderTables-subgraph",
            attributeNodes = @NamedAttributeNode("table")
        )
    }
)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dining_table_id")
    @JsonIgnoreProperties({"orders", "restaurant", "waiterTables", "hibernateLazyInitializer", "handler"})
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private RestaurantTable diningTable;

    /**
     * @deprecated Use {@link #orderTables} instead. This field is kept for backward compatibility
     * during migration and will be removed in a future version.
     */
    @Deprecated
    @Column(name = "table_ids", length = 255)
    private String tableIds;

    /**
     * Tables associated with this order (for multi-table dine-in orders).
     * This replaces the deprecated tableIds comma-separated string field.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<OrderTable> orderTables = new HashSet<>();

    // Guest count for dine-in orders
    @Column(name = "guest_count")
    private Integer guestCount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "waiter_id")
    @JsonIgnoreProperties({"waiterTables", "permissions", "hibernateLazyInitializer", "handler"})
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Waiter waiter;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type")
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_source")
    private OrderSource orderSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OrderStatus status = OrderStatus.NEW;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private PaymentStatus paymentStatus;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal tax;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal discount;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(name = "discount_type", length = 50)
    private String discountType;

    @Column(name = "discount_reason", length = 500)
    private String discountReason;

    @Column(name = "promotion_id")
    private Long promotionId;

    @Column(name = "promotion_name", length = 200)
    private String promotionName;

    @Column(name = "happy_hour_id")
    private Long happyHourId;

    @Column(name = "bonus_used", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal bonusUsed = BigDecimal.ZERO;

    @Column(name = "service_fee_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal serviceFeePercent = BigDecimal.ZERO;

    @Column(name = "service_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal serviceFee = BigDecimal.ZERO;

    @Column(name = "entry_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal entryFee = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "tip_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal tipAmount = BigDecimal.ZERO;

    @Column(name = "grand_total", precision = 10, scale = 2)
    private BigDecimal grandTotal;

    @Column(length = 1000)
    private String customerNotes;

    @Column(length = 1000)
    private String internalNotes;

    @Column(columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime scheduledFor;

    @Column(name = "placed_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime placedAt;

    @Column(name = "accepted_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime acceptedAt;

    @Column(name = "preparing_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime preparingAt;

    @Column(name = "ready_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime readyAt;

    @Column(name = "picked_up_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime pickedUpAt;

    @Column(name = "completed_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime completedAt;

    @Column(name = "cancelled_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime cancelledAt;

    @Column(name = "rejected_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime rejectedAt;

    @Column(name = "cancelled_by", length = 200)
    private String cancelledBy;

    @Column(name = "cancellation_reason", length = 1000)
    private String cancellationReason;

    @Column(name = "void_reason", length = 500)
    private String voidReason;

    @Column(name = "voided_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime voidedAt;

    @Column(name = "voided_by", length = 100)
    private String voidedBy;

    // Soft delete support - financial records should never be hard deleted
    @Column(name = "deleted_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    @Column(name = "payment_intent_id", length = 255)
    private String paymentIntentId;

    // Tax exemption fields
    @Column(name = "is_tax_exempt")
    @Builder.Default
    private Boolean isTaxExempt = false;

    @Column(name = "tax_exemption_type_id")
    private Long taxExemptionTypeId;

    @Column(name = "tax_exemption_number", length = 100)
    private String taxExemptionNumber;

    @Column(name = "tax_exemption_reason", length = 500)
    private String taxExemptionReason;

    // Offline mode fields
    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "client_order_id", length = 100)
    private String clientOrderId;

    @Column(name = "is_offline_order")
    @Builder.Default
    private Boolean isOfflineOrder = false;

    @Column(name = "synced_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime syncedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<OrderItem> items = new HashSet<>();

    @OneToOne(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private DeliveryInfo deliveryInfo;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Payment> payments = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<OrderStatusHistory> statusHistory = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    /**
     * Version field for optimistic locking.
     * Prevents race conditions when multiple users/processes update the same order concurrently
     * (e.g., simultaneous payment processing, status changes, item modifications).
     */
    @Version
    private Long version;

    public void addItem(OrderItem item) {
        // Check if an identical item already exists (same product, variant, addOns, specialInstructions)
        OrderItem existingItem = findMatchingItem(item);

        if (existingItem != null) {
            // Consolidate: update quantity and total price
            existingItem.setQuantity(existingItem.getQuantity() + item.getQuantity());
            existingItem.setTotalPrice(existingItem.getUnitPrice().multiply(
                    java.math.BigDecimal.valueOf(existingItem.getQuantity())));
        } else {
            // Add as new item
            items.add(item);
            item.setOrder(this);
        }
    }

    /**
     * Find an existing item that matches the given item's product, variant, addOns, and special instructions.
     * Returns null if no matching item is found.
     */
    private OrderItem findMatchingItem(OrderItem newItem) {
        for (OrderItem existing : items) {
            if (itemsMatch(existing, newItem)) {
                return existing;
            }
        }
        return null;
    }

    /**
     * Check if two order items match (same product, variant, addOns, special instructions, and bundle).
     */
    private boolean itemsMatch(OrderItem existing, OrderItem newItem) {
        // Must have same productId
        if (!existing.getProductId().equals(newItem.getProductId())) {
            return false;
        }

        // Must have same variantId (both null or same value)
        if (!Objects.equals(existing.getVariantId(), newItem.getVariantId())) {
            return false;
        }

        // Must have same addOns (both null/empty or same value)
        // Use getAddOnsDisplay() which handles both new itemAddOns and deprecated addOns field
        String existingAddOns = existing.getAddOnsDisplay() != null ? existing.getAddOnsDisplay().trim() : "";
        String newAddOns = newItem.getAddOnsDisplay() != null ? newItem.getAddOnsDisplay().trim() : "";
        if (!existingAddOns.equals(newAddOns)) {
            return false;
        }

        // Must have same specialInstructions (both null/empty or same value)
        String existingInstructions = existing.getSpecialInstructions() != null ? existing.getSpecialInstructions().trim() : "";
        String newInstructions = newItem.getSpecialInstructions() != null ? newItem.getSpecialInstructions().trim() : "";
        if (!existingInstructions.equals(newInstructions)) {
            return false;
        }

        // Must have same bundleId (both null or same value)
        if (!Objects.equals(existing.getBundleId(), newItem.getBundleId())) {
            return false;
        }

        // Must have same unit price
        if (existing.getUnitPrice().compareTo(newItem.getUnitPrice()) != 0) {
            return false;
        }

        return true;
    }

    public void addStatusHistory(OrderStatusHistory history) {
        statusHistory.add(history);
        history.setOrder(this);
    }

    public void setDeliveryInfo(DeliveryInfo deliveryInfo) {
        this.deliveryInfo = deliveryInfo;
        if (deliveryInfo != null) {
            deliveryInfo.setOrder(this);
        }
    }

    public void addPayment(Payment payment) {
        payments.add(payment);
        payment.setOrder(this);
    }

    /**
     * Get the primary payment (first payment) - backward compatibility
     */
    public Payment getPayment() {
        if (payments == null || payments.isEmpty()) {
            return null;
        }
        return payments.get(0);
    }

    /**
     * Set a single payment - backward compatibility for single payment flow
     */
    public void setPayment(Payment payment) {
        if (payment == null) {
            return;
        }
        // Clear existing payments and add the new one
        if (this.payments == null) {
            this.payments = new ArrayList<>();
        }
        // For backward compatibility, if setting a single payment, replace the first one
        if (!this.payments.isEmpty()) {
            this.payments.set(0, payment);
        } else {
            this.payments.add(payment);
        }
        payment.setOrder(this);
    }

    /**
     * Get total amount paid across all completed payments
     */
    public BigDecimal getTotalPaid() {
        return payments.stream()
                .filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
                .map(Payment::getNetAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get the effective grand total amount.
     * Falls back to total if grandTotal is null or zero.
     */
    private BigDecimal getEffectiveGrandTotal() {
        if (grandTotal != null && grandTotal.compareTo(BigDecimal.ZERO) > 0) {
            return grandTotal;
        }
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Get remaining balance to be paid
     */
    public BigDecimal getRemainingBalance() {
        return getEffectiveGrandTotal().subtract(getTotalPaid());
    }

    /**
     * Check if order is fully paid
     * An order with total = 0 is NOT considered fully paid (no items to pay for)
     * Exposed as 'fullyPaid' in JSON for frontend use
     */
    @JsonProperty("fullyPaid")
    public boolean isFullyPaid() {
        BigDecimal effectiveTotal = getEffectiveGrandTotal();
        // Order must have a positive total to be considered "fully paid"
        if (effectiveTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return getRemainingBalance().compareTo(BigDecimal.ZERO) <= 0;
    }

    /**
     * Initialize BigDecimal fields to ZERO if null to prevent constraint violations
     */
    @PrePersist
    @PreUpdate
    protected void initializeDefaults() {
        if (subtotal == null) subtotal = BigDecimal.ZERO;
        if (deliveryFee == null) deliveryFee = BigDecimal.ZERO;
        if (tax == null) tax = BigDecimal.ZERO;
        if (discount == null) discount = BigDecimal.ZERO;
        if (serviceFeePercent == null) serviceFeePercent = BigDecimal.ZERO;
        if (serviceFee == null) serviceFee = BigDecimal.ZERO;
        if (entryFee == null) entryFee = BigDecimal.ZERO;
        if (total == null) total = BigDecimal.ZERO;
        if (bonusUsed == null) bonusUsed = BigDecimal.ZERO;
        if (tipAmount == null) tipAmount = BigDecimal.ZERO;
        // Grand total = total + tip (recalculate if null or zero)
        if (grandTotal == null || grandTotal.compareTo(BigDecimal.ZERO) == 0) {
            grandTotal = total.add(tipAmount != null ? tipAmount : BigDecimal.ZERO);
        }
    }

    // ==================== ORDER TABLES HELPER METHODS ====================

    /**
     * Add a table to this order.
     * @param table the restaurant table to add
     * @param isPrimary whether this is the primary table for the order
     */
    public void addTable(com.elcafe.modules.restaurant.entity.RestaurantTable table, boolean isPrimary) {
        if (orderTables == null) {
            orderTables = new HashSet<>();
        }
        OrderTable orderTable = OrderTable.builder()
                .order(this)
                .table(table)
                .isPrimary(isPrimary)
                .build();
        orderTables.add(orderTable);

        // Maintain backward compatibility with diningTable field
        if (isPrimary) {
            this.diningTable = table;
        }
    }

    /**
     * Add a table to this order (non-primary).
     */
    public void addTable(com.elcafe.modules.restaurant.entity.RestaurantTable table) {
        addTable(table, false);
    }

    /**
     * Remove a table from this order.
     */
    public void removeTable(com.elcafe.modules.restaurant.entity.RestaurantTable table) {
        if (orderTables != null) {
            orderTables.removeIf(ot -> ot.getTable().getId().equals(table.getId()));
        }
        if (diningTable != null && diningTable.getId().equals(table.getId())) {
            this.diningTable = null;
        }
    }

    /**
     * Clear all tables from this order.
     */
    public void clearTables() {
        if (orderTables != null) {
            orderTables.clear();
        }
        this.diningTable = null;
    }

    /**
     * Get all table IDs associated with this order.
     * Uses the new orderTables relationship, with fallback to deprecated tableIds field.
     */
    public List<Long> getTableIdList() {
        // Prefer the new relationship
        if (orderTables != null && !orderTables.isEmpty()) {
            return orderTables.stream()
                    .map(ot -> ot.getTable().getId())
                    .collect(Collectors.toList());
        }
        // Fallback to deprecated tableIds field for backward compatibility
        if (tableIds != null && !tableIds.isBlank()) {
            return Arrays.stream(tableIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(Long::parseLong)
                    .collect(Collectors.toList());
        }
        // Fallback to diningTable field
        if (diningTable != null) {
            return List.of(diningTable.getId());
        }
        return Collections.emptyList();
    }

    /**
     * Get all tables associated with this order.
     */
    @JsonIgnore
    public List<com.elcafe.modules.restaurant.entity.RestaurantTable> getTables() {
        if (orderTables != null && !orderTables.isEmpty()) {
            return orderTables.stream()
                    .map(OrderTable::getTable)
                    .collect(Collectors.toList());
        }
        if (diningTable != null) {
            return List.of(diningTable);
        }
        return Collections.emptyList();
    }

    /**
     * Get the primary table for this order.
     */
    @JsonIgnore
    public com.elcafe.modules.restaurant.entity.RestaurantTable getPrimaryTable() {
        if (orderTables != null && !orderTables.isEmpty()) {
            return orderTables.stream()
                    .filter(ot -> Boolean.TRUE.equals(ot.getIsPrimary()))
                    .map(OrderTable::getTable)
                    .findFirst()
                    .orElseGet(() -> orderTables.iterator().next().getTable());
        }
        return diningTable;
    }

    /**
     * Check if this order has any tables assigned.
     */
    public boolean hasTables() {
        return (orderTables != null && !orderTables.isEmpty())
                || (tableIds != null && !tableIds.isBlank())
                || diningTable != null;
    }

    // ==================== SOFT DELETE METHODS ====================

    /**
     * Check if this order has been soft-deleted.
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Soft delete this order. Financial records should never be hard deleted.
     */
    public void softDelete(String deletedByUser) {
        this.deletedAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.deletedBy = deletedByUser;
    }

    /**
     * Restore a soft-deleted order.
     */
    public void restore() {
        this.deletedAt = null;
        this.deletedBy = null;
    }
}
