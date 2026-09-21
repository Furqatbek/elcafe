package com.elcafe.modules.loyalty.repository;

import com.elcafe.modules.loyalty.entity.TierHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TierHistoryRepository extends JpaRepository<TierHistory, Long> {

    List<TierHistory> findByCustomerLoyaltyIdOrderByCreatedAtDesc(Long customerLoyaltyId);

    Page<TierHistory> findByCustomerLoyaltyIdOrderByCreatedAtDesc(Long customerLoyaltyId, Pageable pageable);
}
