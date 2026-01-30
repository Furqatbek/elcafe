package com.elcafe.modules.order.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Represents an add-on/modifier selected for an order item.
 * Captures the add-on details at order time to maintain price integrity
 * and enable querying/reporting on modifier usage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "order_item_add_ons")
public class OrderItemAddOn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    @JsonIgnore
    private OrderItem orderItem;

    /**
     * Reference to the original AddOn entity for tracking and reporting.
     * Nullable to handle cases where add-ons are deleted or custom modifiers are used.
     */
    @Column(name = "add_on_id")
    private Long addOnId;

    /**
     * Add-on name captured at order time.
     * Stored separately to preserve historical accuracy even if the add-on is later modified.
     */
    @Column(name = "add_on_name", nullable = false, length = 200)
    private String addOnName;

    /**
     * Add-on price captured at order time.
     * Stored separately to ensure price integrity - the price charged is preserved
     * even if the add-on's current price changes.
     */
    @Column(name = "add_on_price", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal addOnPrice = BigDecimal.ZERO;

    /**
     * Quantity of this add-on (e.g., "extra cheese x2").
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    /**
     * Calculate the total price for this add-on (price * quantity).
     */
    public BigDecimal getTotalPrice() {
        return addOnPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
