package com.elcafe.modules.selfservice.entity;

import com.elcafe.modules.menu.entity.Product;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.elcafe.modules.menu.entity.ProductVariant;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Cart item for self-service ordering.
 */
@Entity
@Table(name = "self_service_cart_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SelfServiceCartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private SelfServiceSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(name = "bundle_id")
    private Long bundleId;

    @Column(name = "bundle_name")
    private String bundleName;

    @Column(name = "is_bundle")
    @Builder.Default
    private Boolean isBundle = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id")
    private ProductVariant variant;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "special_instructions", length = 500)
    private String specialInstructions;

    @Column(name = "added_at")
    @Builder.Default
    private LocalDateTime addedAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "cartItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore
    private List<SelfServiceCartModifier> modifiers = new ArrayList<>();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Calculate total price for this cart item including modifiers.
     * Modifiers are stored with their total quantity already, so we don't multiply
     * by item quantity again to avoid double-counting.
     */
    public BigDecimal getTotalPrice() {
        BigDecimal itemTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
        // Modifiers are applied per unit, multiply by item quantity
        BigDecimal modifiersPerUnit = modifiers.stream()
                .map(m -> m.getPrice().multiply(BigDecimal.valueOf(m.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Total = (unit price * quantity) + (modifiers per unit * quantity)
        return itemTotal.add(modifiersPerUnit.multiply(BigDecimal.valueOf(quantity)));
    }

    /**
     * Get the unit price including modifiers for a single item.
     * This is useful for display purposes.
     */
    public BigDecimal getUnitPriceWithModifiers() {
        BigDecimal modifiersPerUnit = modifiers.stream()
                .map(m -> m.getPrice().multiply(BigDecimal.valueOf(m.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return unitPrice.add(modifiersPerUnit);
    }
}
