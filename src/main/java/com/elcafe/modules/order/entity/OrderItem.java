package com.elcafe.modules.order.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, length = 200)
    private String productName;

    private Long variantId;

    @Column(length = 200)
    private String variantName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    /**
     * @deprecated Use {@link #itemAddOns} instead. This field is kept for backward compatibility
     * during migration and will be removed in a future version.
     */
    @Deprecated
    @Column(columnDefinition = "TEXT")
    private String addOns;

    /**
     * Add-ons/modifiers selected for this order item.
     * This replaces the deprecated addOns comma-separated string field.
     */
    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItemAddOn> itemAddOns = new ArrayList<>();

    @Column(length = 500)
    private String specialInstructions;

    // Bundle reference (if this item was part of a bundle/combo)
    @Column(name = "bundle_id")
    private Long bundleId;

    @Column(name = "bundle_name", length = 200)
    private String bundleName;

    // Flag to indicate if this is a bundle item
    @Column(name = "is_bundle")
    @Builder.Default
    private Boolean isBundle = false;

    // ==================== ADD-ON HELPER METHODS ====================

    /**
     * Add an add-on to this order item.
     */
    public void addAddOn(OrderItemAddOn addOn) {
        if (itemAddOns == null) {
            itemAddOns = new ArrayList<>();
        }
        itemAddOns.add(addOn);
        addOn.setOrderItem(this);
    }

    /**
     * Add an add-on by details.
     */
    public void addAddOn(Long addOnId, String name, BigDecimal price, Integer quantity) {
        OrderItemAddOn addOn = OrderItemAddOn.builder()
                .addOnId(addOnId)
                .addOnName(name)
                .addOnPrice(price != null ? price : BigDecimal.ZERO)
                .quantity(quantity != null ? quantity : 1)
                .build();
        addAddOn(addOn);
    }

    /**
     * Remove an add-on from this order item.
     */
    public void removeAddOn(OrderItemAddOn addOn) {
        if (itemAddOns != null) {
            itemAddOns.remove(addOn);
        }
    }

    /**
     * Clear all add-ons from this order item.
     */
    public void clearAddOns() {
        if (itemAddOns != null) {
            itemAddOns.clear();
        }
    }

    /**
     * Get total price of all add-ons.
     */
    public BigDecimal getAddOnsTotal() {
        if (itemAddOns == null || itemAddOns.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return itemAddOns.stream()
                .map(OrderItemAddOn::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get add-ons as a formatted display string.
     * Uses the new itemAddOns relationship, with fallback to deprecated addOns field.
     */
    public String getAddOnsDisplay() {
        // Prefer the new relationship
        if (itemAddOns != null && !itemAddOns.isEmpty()) {
            return itemAddOns.stream()
                    .map(ao -> {
                        String display = ao.getAddOnName();
                        if (ao.getQuantity() > 1) {
                            display += " x" + ao.getQuantity();
                        }
                        if (ao.getAddOnPrice().compareTo(BigDecimal.ZERO) > 0) {
                            display += " (+$" + ao.getAddOnPrice() + ")";
                        }
                        return display;
                    })
                    .collect(Collectors.joining(", "));
        }
        // Fallback to deprecated field for backward compatibility
        return addOns;
    }

    /**
     * Check if this order item has any add-ons.
     */
    public boolean hasAddOns() {
        return (itemAddOns != null && !itemAddOns.isEmpty())
                || (addOns != null && !addOns.isBlank());
    }
}
