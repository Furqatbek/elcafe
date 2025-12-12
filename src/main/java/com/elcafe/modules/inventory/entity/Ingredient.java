package com.elcafe.modules.inventory.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
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
@Entity
@Table(name = "inventory_ingredients")
@EntityListeners(AuditingEntityListener.class)
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
    private BigDecimal costPerUnit;

    @Column(length = 100)
    private String supplier;

    @Column(length = 50)
    private String sku;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean trackInventory = true;

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
}
