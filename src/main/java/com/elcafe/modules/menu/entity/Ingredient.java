package com.elcafe.modules.menu.entity;

import com.elcafe.modules.menu.enums.IngredientCategory;
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

/**
 * Ingredient entity for menu items
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ingredients")
@EntityListeners(AuditingEntityListener.class)
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200, unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 50)
    private String unit; // kg, liter, piece, gram, etc.

    @Column(name = "cost_per_unit", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal costPerUnit = BigDecimal.ZERO;

    @Column(name = "current_stock", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal currentStock = BigDecimal.ZERO;

    @Column(name = "minimum_stock", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal minimumStock = BigDecimal.ZERO;

    @Column(length = 200)
    private String supplier;

    @Enumerated(EnumType.STRING)
    @Column(length = 100)
    private IngredientCategory category;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Check if ingredient is low on stock
     */
    public boolean isLowStock() {
        return currentStock.compareTo(minimumStock) <= 0;
    }
}
