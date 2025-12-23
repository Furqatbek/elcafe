package com.elcafe.modules.pricing.repository;

import com.elcafe.modules.pricing.entity.PriceChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PriceChangeLogRepository extends JpaRepository<PriceChangeLog, Long> {

    List<PriceChangeLog> findByProductIdOrderByCreatedAtDesc(Long productId);

    List<PriceChangeLog> findByRestaurant_IdOrderByCreatedAtDesc(Long restaurantId);

    List<PriceChangeLog> findByRestaurant_IdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long restaurantId,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    @Query("SELECT p FROM PriceChangeLog p WHERE p.restaurantId = :restaurantId AND p.createdAt >= :since ORDER BY p.createdAt DESC")
    List<PriceChangeLog> findRecentByRestaurantId(@Param("restaurantId") Long restaurantId, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(p) FROM PriceChangeLog p WHERE p.restaurantId = :restaurantId AND p.recommendationAccepted = true AND p.createdAt >= :since")
    Long countAcceptedRecommendations(@Param("restaurantId") Long restaurantId, @Param("since") LocalDateTime since);

    @Query("SELECT AVG(p.priceChangePercentage) FROM PriceChangeLog p WHERE p.restaurantId = :restaurantId AND p.createdAt >= :since")
    Double getAveragePriceChangePercentage(@Param("restaurantId") Long restaurantId, @Param("since") LocalDateTime since);
}
