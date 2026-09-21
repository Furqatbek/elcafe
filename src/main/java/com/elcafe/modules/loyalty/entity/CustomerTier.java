package com.elcafe.modules.loyalty.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "customer_tiers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(nullable = false, unique = true)
    private Integer level;

    @Builder.Default
    @Column(name = "min_total_spend", precision = 10, scale = 2)
    private BigDecimal minTotalSpend = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "min_order_count")
    private Integer minOrderCount = 0;

    @Builder.Default
    @Column(name = "bonus_multiplier", precision = 3, scale = 2)
    private BigDecimal bonusMultiplier = BigDecimal.ONE;

    @Column(name = "benefits_description", columnDefinition = "TEXT")
    private String benefitsDescription;

    @Column(length = 20)
    private String color;

    @Column(length = 50)
    private String icon;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
