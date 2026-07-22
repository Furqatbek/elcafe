package com.elcafe.modules.loyalty.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "tier_history", indexes = {
    @Index(name = "idx_tier_history_customer_loyalty_id", columnList = "customer_loyalty_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TierHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_loyalty_id", nullable = false)
    private CustomerLoyalty customerLoyalty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_tier_id")
    private CustomerTier fromTier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_tier_id", nullable = false)
    private CustomerTier toTier;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
