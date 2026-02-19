package com.elcafe.modules.loyalty.repository;

import com.elcafe.modules.loyalty.entity.MilestoneRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MilestoneRedemptionRepository extends JpaRepository<MilestoneRedemption, Long> {

    Optional<MilestoneRedemption> findByMilestoneIdAndCustomerId(Long milestoneId, Long customerId);

    List<MilestoneRedemption> findByCustomerId(Long customerId);

    @Query("SELECT mr FROM MilestoneRedemption mr WHERE mr.customer.id = :customerId AND mr.rewardPending = true")
    List<MilestoneRedemption> findPendingRewards(@Param("customerId") Long customerId);

    @Query("SELECT mr FROM MilestoneRedemption mr " +
           "JOIN mr.milestone m WHERE mr.customer.id = :customerId AND m.active = true " +
           "AND (m.restaurant.id = :restaurantId OR m.restaurant IS NULL)")
    List<MilestoneRedemption> findByCustomerIdAndRestaurantId(
        @Param("customerId") Long customerId,
        @Param("restaurantId") Long restaurantId
    );
}
