package com.elcafe.modules.partner.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * Correlates a partner's own order identifier with ours, permanently.
 *
 * <p>Two jobs. It answers the question every delivery dispute starts with — "their order 88213 is our
 * ORD-000417" — and it is the durable half of deduplication: the generic idempotency key collapses a
 * retry within 24 hours, but this unique {@code (partner_id, external_order_id)} still refuses a
 * replay months later, when the key has long expired and a second kitchen ticket would otherwise be
 * printed for an order the venue already cooked.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "partner_orders",
        uniqueConstraints = @UniqueConstraint(name = "uq_partner_order_external",
                columnNames = {"partner_id", "external_order_id"}))
@EntityListeners(AuditingEntityListener.class)
public class PartnerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "partner_id", nullable = false)
    private Long partnerId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    /** The identifier the partner uses for this order in their own system. */
    @Column(name = "external_order_id", nullable = false, length = 190)
    private String externalOrderId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;
}
