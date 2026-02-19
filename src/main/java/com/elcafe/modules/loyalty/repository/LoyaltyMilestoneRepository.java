package com.elcafe.modules.loyalty.repository;

import com.elcafe.modules.loyalty.entity.LoyaltyMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoyaltyMilestoneRepository extends JpaRepository<LoyaltyMilestone, Long> {

    List<LoyaltyMilestone> findByRestaurant_IdAndActive(Long restaurantId, Boolean active);

    @Query("SELECT lm FROM LoyaltyMilestone lm WHERE lm.active = true AND " +
           "(lm.restaurant.id = :restaurantId OR lm.restaurant IS NULL)")
    List<LoyaltyMilestone> findActiveMilestonesForRestaurant(@Param("restaurantId") Long restaurantId);

    List<LoyaltyMilestone> findByRestaurant_Id(Long restaurantId);
}
