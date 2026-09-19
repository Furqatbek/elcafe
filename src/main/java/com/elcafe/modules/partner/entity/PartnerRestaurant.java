package com.elcafe.modules.partner.entity;

import com.elcafe.modules.partner.enums.PriceAdjustmentType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Grants one {@link Partner} access to one restaurant. This row <b>is</b> the authorization decision:
 * with no active row a partner cannot see that venue's menu or push it an order, however valid its API
 * key is. Revoking a venue is therefore a local act that leaves the partner's other venues alone.
 *
 * <p>Menu-read defaults on and order-push defaults off, so onboarding starts read-only and writing into
 * a venue's kitchen stays a separate, deliberate decision.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "partner_restaurants",
        uniqueConstraints = @UniqueConstraint(name = "uq_partner_restaurant",
                columnNames = {"partner_id", "restaurant_id"}))
@EntityListeners(AuditingEntityListener.class)
public class PartnerRestaurant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "partner_id", nullable = false)
    private Long partnerId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(name = "can_read_menu", nullable = false)
    @Builder.Default
    private Boolean canReadMenu = true;

    @Column(name = "can_push_orders", nullable = false)
    @Builder.Default
    private Boolean canPushOrders = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * The default channel markup for this partner at this venue — the commercial term behind the whole
     * feature: an aggregator takes a commission, so the shelf price there is normally above the counter
     * price. NONE by default, so upgrading never silently reprices a live integration.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "price_adjustment_type", nullable = false, length = 10)
    @Builder.Default
    private PriceAdjustmentType priceAdjustmentType = PriceAdjustmentType.NONE;

    /** Percent (15 → +15%) or absolute amount (500 → +500), per {@link #priceAdjustmentType}. */
    @Column(name = "price_adjustment_value", nullable = false, precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal priceAdjustmentValue = BigDecimal.ZERO;

    /**
     * Round the marked-up price to a multiple of this; 0 disables it. The partner displays our number
     * verbatim, so a markup that lands on 34 567 has to be tidied here, not on their side.
     */
    @Column(name = "price_rounding", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal priceRounding = BigDecimal.ZERO;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;
}
