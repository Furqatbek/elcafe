package com.elcafe.modules.partner.repository;

import com.elcafe.modules.partner.entity.PartnerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartnerOrderRepository extends JpaRepository<PartnerOrder, Long> {

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
