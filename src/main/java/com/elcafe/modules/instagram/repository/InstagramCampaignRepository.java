package com.elcafe.modules.instagram.repository;

import com.elcafe.modules.instagram.entity.InstagramCampaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InstagramCampaignRepository extends JpaRepository<InstagramCampaign, Long> {

    /** Tenant-scoped by-id lookup — closes the IDOR a bare {@code findById} leaves open. */
    Optional<InstagramCampaign> findByIdAndRestaurantId(Long id, Long restaurantId);

    /** One tenant's campaigns, newest first. */
    Page<InstagramCampaign> findByRestaurantIdOrderByIdDesc(Long restaurantId, Pageable pageable);
}
