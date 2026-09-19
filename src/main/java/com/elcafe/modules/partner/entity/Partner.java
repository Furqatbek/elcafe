package com.elcafe.modules.partner.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * An external system that integrates with us over the partner API — today a delivery aggregator that
 * pulls a venue's menu and pushes customer orders back in.
 *
 * <p>Unlike every other actor in this schema a partner is <b>not</b> scoped to one restaurant: the same
 * aggregator lists many of our venues. It therefore carries no {@code restaurantId} and is not covered
 * by the {@code restaurantFilter}; what it may touch is decided entirely by {@link PartnerRestaurant}
 * grants. Nothing should ever read a partner and assume access follows from existence.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "partners", uniqueConstraints = {
        @UniqueConstraint(name = "uq_partners_slug", columnNames = "slug"),
        @UniqueConstraint(name = "uq_partners_api_key_hash", columnNames = "api_key_hash")
})
@EntityListeners(AuditingEntityListener.class)
public class Partner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    /**
     * Stable handle used to namespace this partner's idempotency keys. Immutable once issued —
     * {@code idempotency_key} is globally unique, so changing a slug would let a replayed order from
     * the old namespace be treated as new.
     */
    @Column(nullable = false, length = 100)
    private String slug;

    /**
     * SHA-256 hex of the issued API key. The raw key exists only in the response to the create/rotate
     * call and is never persisted, so a database read cannot authenticate as the partner.
     */
    @JsonIgnore
    @Column(name = "api_key_hash", nullable = false, length = 64)
    private String apiKeyHash;

    /** Leading characters of the raw key, so an operator can tell two keys apart. Not secret. */
    @Column(name = "api_key_prefix", length = 16)
    private String apiKeyPrefix;

    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * What this partner adds at their own checkout, as a percentage of the price we publish (V192).
     *
     * <p><b>Display only.</b> Nothing prices an item with it, nothing charges it, and no menu we
     * publish includes it: it is their charge, levied by them, and we could not collect it if we
     * wanted to. It exists so a venue setting a channel markup can see what their customer will
     * actually pay, instead of setting a lever whose effect they cannot see.
     *
     * <p>Zero means none, or that we have not been told — a partner who has not told us what they add
     * gets no speculative arithmetic shown against their name.
     */
    @Column(name = "customer_fee_percent", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private java.math.BigDecimal customerFeePercent = java.math.BigDecimal.ZERO;

    /**
     * Whether this partner's {@code paymentMode} says how the customer actually paid, or is a constant
     * they have not implemented yet (V194).
     *
     * <p>It is not decoration: {@code PREPAID} creates the payment settled and the kitchen ticket
     * prints as paid, so a counter hand gives the bag to a courier who owes nothing. A partner who
     * sends the field by default rather than by fact can lose a venue a meal per cash order.
     *
     * <p>While this is false the partner cannot be granted order push at any venue. Menu reads and
     * status reports are unaffected — it is only the orders that carry money. False by default,
     * because "they said they would tell us before switching it on" is a promise somebody has to
     * remember, and this is the same thing as a switch.
     */
    @Column(name = "payment_mode_confirmed", nullable = false)
    @Builder.Default
    private Boolean paymentModeConfirmed = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;
}
