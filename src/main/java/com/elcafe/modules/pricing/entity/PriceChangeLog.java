package com.elcafe.modules.pricing.entity;

import com.elcafe.modules.pricing.enums.PricingStrategy;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity for tracking price change history for audit and analysis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "price_change_logs")
@EntityListeners(AuditingEntityListener.class)
public class PriceChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "previous_price", precision = 10, scale = 2)
    private BigDecimal previousPrice;

    @Column(name = "new_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal newPrice;

    @Column(name = "price_change", precision = 10, scale = 2)
    private BigDecimal priceChange;

    @Column(name = "price_change_percentage", precision = 5, scale = 2)
    private BigDecimal priceChangePercentage;

    @Column(name = "previous_cost_price", precision = 10, scale = 2)
    private BigDecimal previousCostPrice;

    @Column(name = "new_cost_price", precision = 10, scale = 2)
    private BigDecimal newCostPrice;

    @Column(name = "previous_margin_percentage", precision = 5, scale = 2)
    private BigDecimal previousMarginPercentage;

    @Column(name = "new_margin_percentage", precision = 5, scale = 2)
    private BigDecimal newMarginPercentage;

    @Enumerated(EnumType.STRING)
    @Column(name = "pricing_strategy", length = 50)
    private PricingStrategy pricingStrategy;

    @Column(name = "change_reason", length = 500)
    private String changeReason;

    @Column(name = "changed_by", length = 200)
    private String changedBy;

    @Column(name = "is_system_generated")
    @Builder.Default
    private Boolean isSystemGenerated = false;

    @Column(name = "recommendation_accepted")
    private Boolean recommendationAccepted;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
