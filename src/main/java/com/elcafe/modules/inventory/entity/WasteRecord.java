package com.elcafe.modules.inventory.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "inventory_waste_records", indexes = {
        @Index(name = "idx_waste_records_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_waste_records_ingredient", columnList = "ingredient_id"),
        @Index(name = "idx_waste_records_date", columnList = "waste_date"),
        @Index(name = "idx_waste_records_reason", columnList = "waste_reason")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class WasteRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private InventoryBatch batch;

    @Column(name = "waste_date", nullable = false)
    private LocalDate wasteDate;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost", precision = 15, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "total_cost", precision = 15, scale = 2)
    private BigDecimal totalCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "waste_reason", nullable = false, length = 30)
    private WasteReason wasteReason;

    @Column(name = "recorded_by", length = 100)
    private String recordedBy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum WasteReason {
        EXPIRED("Expired", "Product past expiry date"),
        SPOILED("Spoiled", "Product spoiled before expiry"),
        DAMAGED("Damaged", "Physical damage during handling"),
        PREPARATION("Preparation", "Normal preparation waste/trimmings"),
        OVER_PRODUCTION("Over Production", "Excess food prepared but not sold"),
        CUSTOMER_RETURN("Customer Return", "Returned by customer"),
        QUALITY_ISSUE("Quality Issue", "Did not meet quality standards"),
        CONTAMINATION("Contamination", "Cross-contamination or hygiene issue"),
        THEFT("Theft", "Suspected or confirmed theft"),
        OTHER("Other", "Other reason");

        private final String label;
        private final String description;

        WasteReason(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public String getLabel() {
            return label;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Calculate total cost based on quantity and unit cost
     */
    public void calculateTotalCost() {
        if (quantity != null && unitCost != null) {
            this.totalCost = quantity.multiply(unitCost);
        }
    }

    /**
     * Set unit cost from ingredient if not provided
     * Uses ingredient's effective cost (WAC if available, else costPerUnit)
     */
    public void setUnitCostFromIngredient() {
        if (unitCost == null && ingredient != null) {
            // Use effective cost which prefers WAC over static costPerUnit
            BigDecimal effectiveCost = ingredient.getEffectiveCost();
            if (effectiveCost != null && effectiveCost.compareTo(BigDecimal.ZERO) > 0) {
                this.unitCost = effectiveCost;
            }
        }
    }

    @PrePersist
    @PreUpdate
    private void prePersist() {
        setUnitCostFromIngredient();
        calculateTotalCost();
    }
}
