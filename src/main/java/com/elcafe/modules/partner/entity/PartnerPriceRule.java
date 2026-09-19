package com.elcafe.modules.partner.entity;

import com.elcafe.modules.partner.enums.PriceAdjustmentType;
import com.elcafe.modules.partner.enums.PriceRuleScope;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * An exception to a partner's default channel markup, for one category, product or variant.
 *
 * <p>{@link #targetId} is deliberately not a foreign key: what it points at depends on {@link #scope},
 * and no single constraint can express that. The cost is orphan rows when a product is deleted, and it
 * is acceptable because resolution only looks up rules for items it has already loaded — an orphan rule
 * is inert rather than wrong.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "partner_price_rules",
        uniqueConstraints = @UniqueConstraint(name = "uq_partner_price_rule",
                columnNames = {"partner_id", "restaurant_id", "scope", "target_id"}))
@EntityListeners(AuditingEntityListener.class)
public class PartnerPriceRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "partner_id", nullable = false)
    private Long partnerId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PriceRuleScope scope;

    /** Id of the category, product or variant this rule applies to, per {@link #scope}. */
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "adjustment_type", nullable = false, length = 10)
    private PriceAdjustmentType adjustmentType;

    @Column(name = "adjustment_value", nullable = false, precision = 10, scale = 4)
    private BigDecimal adjustmentValue;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;
}
