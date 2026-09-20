package com.elcafe.modules.partner.repository;

import com.elcafe.modules.partner.dto.OwedTicketRow;
import com.elcafe.modules.partner.entity.PartnerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PartnerOrderRepository extends JpaRepository<PartnerOrder, Long> {

    /**
     * Every ticket at this venue that a partner cancelled after our kitchen cutoff (V193).
     *
     * <p>Rooted on {@code Order} rather than on this repository's own entity because that is where the
     * refusal is stamped; {@code PartnerOrder} joins in to name the partner and carry their own order
     * reference, which is what a settlement conversation is conducted in. A theta join rather than an
     * association: the mapping row holds {@code orderId} as a plain column on purpose, so that losing
     * a partner never cascades into an order.
     *
     * <p>No pagination. These are rare by construction — a handful a month at a busy venue — and a
     * total computed over a page would be a different number from the one in the heading.
     */
    @Query("SELECT new com.elcafe.modules.partner.dto.OwedTicketRow("
            + "o.id, o.orderNumber, p.name, po.externalOrderId, "
            + "o.partnerCancelRefusedAt, o.partnerCancelRefusedStage, o.partnerCancelRefusedReason, "
            + "o.subtotal, o.createdAt) "
            + "FROM Order o, PartnerOrder po, Partner p "
            + "WHERE po.orderId = o.id AND p.id = po.partnerId "
            + "AND o.restaurant.id = :restaurantId "
            + "AND o.partnerCancelRefusedAt IS NOT NULL "
            + "AND o.partnerCancelRefusedAt >= :from AND o.partnerCancelRefusedAt < :to "
            + "ORDER BY o.partnerCancelRefusedAt DESC")
    List<OwedTicketRow> findOwedTickets(@Param("restaurantId") Long restaurantId,
                                        @Param("from") OffsetDateTime from,
                                        @Param("to") OffsetDateTime to);

    /**
     * The dedupe lookup, scoped by venue as well as partner.
     *
     * <p>Leaving the restaurant out looks harmless and is not: aggregators commonly number orders per
     * store, so partner P pushing its order "1001" to venue 11 and then to venue 12 would match the
     * first row, be told "duplicate", and venue 12 would never see the order — silently, and repeatedly.
     */
    Optional<PartnerOrder> findByPartnerIdAndRestaurantIdAndExternalOrderId(
            Long partnerId, Long restaurantId, String externalOrderId);

    List<PartnerOrder> findByPartnerIdAndExternalOrderId(Long partnerId, String externalOrderId);

    Optional<PartnerOrder> findByOrderId(Long orderId);
}
