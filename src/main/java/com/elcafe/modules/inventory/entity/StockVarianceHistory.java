package com.elcafe.modules.inventory.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "stock_variance_history", indexes = {
        @Index(name = "idx_variance_history_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_variance_history_ingredient", columnList = "ingredient_id"),
        @Index(name = "idx_variance_history_date", columnList = "variance_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockVarianceHistory {

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
    @JoinColumn(name = "stock_count_id")
    private StockCount stockCount;

    @Column(name = "variance_date", nullable = false)
    private LocalDate varianceDate;

    @Column(name = "system_quantity", nullable = false, precision = 10, scale = 3)
    private BigDecimal systemQuantity;

    @Column(name = "actual_quantity", nullable = false, precision = 10, scale = 3)
    private BigDecimal actualQuantity;

    @Column(name = "variance_quantity", nullable = false, precision = 10, scale = 3)
    private BigDecimal varianceQuantity;

    @Column(name = "variance_percentage", precision = 5, scale = 2)
    private BigDecimal variancePercentage;

    @Column(name = "variance_value", precision = 15, scale = 2)
    private BigDecimal varianceValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "variance_reason", length = 50)
    private StockCountItem.VarianceReason varianceReason;

    @Column(name = "adjustment_made")
    @Builder.Default
    private Boolean adjustmentMade = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Create variance history from a stock count item
     */
    public static StockVarianceHistory fromStockCountItem(StockCountItem item) {
        return StockVarianceHistory.builder()
                .restaurant(item.getStockCount().getRestaurant())
                .ingredient(item.getIngredient())
                .stockCount(item.getStockCount())
                .varianceDate(LocalDate.now())
                .systemQuantity(item.getSystemQuantity())
                .actualQuantity(item.getCountedQuantity())
                .varianceQuantity(item.getVarianceQuantity())
                .variancePercentage(item.getVariancePercentage())
                .varianceValue(item.getVarianceValue())
                .varianceReason(item.getVarianceReason())
                .adjustmentMade(false)
                .build();
    }
}
