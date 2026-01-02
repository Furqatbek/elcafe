package com.elcafe.modules.inventory.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity(name = "InventoryIngredient")
@Table(name = "inventory_ingredients")
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 50)
    private String unit; // kg, g, L, ml, pieces, etc.

    @Column(nullable = false, precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal currentStock = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal minimumStock = BigDecimal.ZERO;

    @Column(nullable = false, precision = 10, scale = 3)
    @Builder.Default
    private BigDecimal reorderLevel = BigDecimal.ZERO;

    @Column(precision = 10, scale = 2)
    private BigDecimal reorderQuantity;

    @Column(precision = 10, scale = 2)
    private BigDecimal costPerUnit;

    // Weighted Average Cost - recalculated on each purchase
    @Column(precision = 15, scale = 4)
    private BigDecimal weightedAverageCost;

    // Last time cost was updated
    @Column
    private LocalDateTime lastCostUpdate;

    @Column(length = 100)
    private String supplier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplierEntity;

    @Column(length = 50)
    private String sku;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean trackInventory = true;

    // Expiry tracking fields
    @Column(nullable = false)
    @Builder.Default
    private Boolean trackExpiry = false;

    @Column
    private Integer defaultShelfLifeDays; // Auto-calculate expiry date from this

    @Column
    @Builder.Default
    private Integer expiryAlertDays = 7; // Days before expiry to trigger alert

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Helper methods
    public boolean isLowStock() {
        return currentStock.compareTo(minimumStock) <= 0;
    }

    public boolean needsReorder() {
        return currentStock.compareTo(reorderLevel) <= 0;
    }

    public boolean hasStock(BigDecimal requiredQuantity) {
        if (!trackInventory) {
            return true;
        }
        return currentStock.compareTo(requiredQuantity) >= 0;
    }

    public void addStock(BigDecimal quantity) {
        this.currentStock = this.currentStock.add(quantity);
    }

    public void deductStock(BigDecimal quantity) {
        this.currentStock = this.currentStock.subtract(quantity);
        if (this.currentStock.compareTo(BigDecimal.ZERO) < 0) {
            this.currentStock = BigDecimal.ZERO;
        }
    }

    /**
     * Get the effective cost per unit (WAC if available, else costPerUnit)
     */
    public BigDecimal getEffectiveCost() {
        if (weightedAverageCost != null && weightedAverageCost.compareTo(BigDecimal.ZERO) > 0) {
            return weightedAverageCost;
        }
        return costPerUnit != null ? costPerUnit : BigDecimal.ZERO;
    }

    /**
     * Update weighted average cost after a new purchase
     * Formula: WAC = (Existing Value + New Purchase Value) / (Existing Qty + New Qty)
     */
    public void updateWeightedAverageCost(BigDecimal newQuantity, BigDecimal newCostPerUnit) {
        if (newQuantity == null || newCostPerUnit == null ||
            newQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal existingValue = currentStock.multiply(getEffectiveCost());
        BigDecimal newValue = newQuantity.multiply(newCostPerUnit);
        BigDecimal totalQuantity = currentStock.add(newQuantity);

        if (totalQuantity.compareTo(BigDecimal.ZERO) > 0) {
            this.weightedAverageCost = existingValue.add(newValue)
                    .divide(totalQuantity, 4, java.math.RoundingMode.HALF_UP);
            this.lastCostUpdate = LocalDateTime.now();
        }
    }

    /**
     * Calculate total inventory value using effective cost
     */
    public BigDecimal getInventoryValue() {
        return currentStock.multiply(getEffectiveCost());
    }
}
