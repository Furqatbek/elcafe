package com.elcafe.modules.partner.repository;

import com.elcafe.modules.partner.entity.PartnerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PartnerOrderRepository extends JpaRepository<PartnerOrder, Long> {

    Optional<PartnerOrder> findByPartnerIdAndExternalOrderId(Long partnerId, String externalOrderId);

    Optional<PartnerOrder> findByOrderId(Long orderId);
}
