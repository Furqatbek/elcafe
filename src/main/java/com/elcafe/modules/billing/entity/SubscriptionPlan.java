package com.elcafe.modules.billing.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A subscription tier (Start / Advance / Pro). Tiers differ primarily by module access, expressed as
 * a set of feature-code strings (e.g. {@code "kitchen.dashboard"}) stored as a JSONB array.
 *
 * <p>This is a platform-level catalogue, NOT a tenant-scoped entity — it has no {@code restaurant_id}
 * and carries no {@code restaurantFilter}. Phase 1 / mini-phase A1 introduces the data shape only;
 * no gating logic reads {@code featureCodes} yet. See {@code docs/subscription-tiers-plan.md}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "subscription_plan")
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable machine code: {@code start}, {@code advance}, {@code pro}. */
    @Column(nullable = false, unique = true, length = 32)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    /** Monthly price in UZS so'm (no fractional unit). 0 until pricing is finalised. */
    @Column(name = "monthly_price", nullable = false)
    @Builder.Default
    private Long monthlyPrice = 0L;

    /**
     * Feature codes unlocked by this tier. JSONB array; empty until the module-to-tier mapping is
     * finalised (mini-phase A4).
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feature_codes", nullable = false)
    @Builder.Default
    private Set<String> featureCodes = new LinkedHashSet<>();

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
