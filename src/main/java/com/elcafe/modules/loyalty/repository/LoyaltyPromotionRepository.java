package com.elcafe.modules.loyalty.repository;

import com.elcafe.modules.loyalty.entity.LoyaltyPromotion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LoyaltyPromotionRepository extends JpaRepository<LoyaltyPromotion, Long> {

    @Query("SELECT lp FROM LoyaltyPromotion lp WHERE " +
           "lp.active = true AND " +
           "lp.startDate <= :now AND " +
           "(lp.endDate IS NULL OR lp.endDate >= :now) AND " +
           "(lp.restaurant.id = :restaurantId OR lp.restaurant IS NULL)")
    List<LoyaltyPromotion> findActivePromotions(
        @Param("restaurantId") Long restaurantId,
        @Param("now") LocalDateTime now
    );

    List<LoyaltyPromotion> findByRestaurantIdAndActive(Long restaurantId, Boolean active);

    @Query("SELECT lp FROM LoyaltyPromotion lp WHERE lp.restaurant IS NULL AND lp.active = true")
    List<LoyaltyPromotion> findGlobalActivePromotions();
}
