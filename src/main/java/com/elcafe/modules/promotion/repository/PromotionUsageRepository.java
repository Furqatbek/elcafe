package com.elcafe.modules.promotion.repository;

import com.elcafe.modules.promotion.entity.PromotionUsage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PromotionUsageRepository extends JpaRepository<PromotionUsage, Long> {

    List<PromotionUsage> findByPromotion_Id(Long promotionId);

    Page<PromotionUsage> findByPromotion_IdOrderByUsedAtDesc(Long promotionId, Pageable pageable);

    List<PromotionUsage> findByCustomer_Id(Long customerId);

    Optional<PromotionUsage> findByOrder_Id(Long orderId);

    @Query("SELECT COUNT(pu) FROM PromotionUsage pu WHERE pu.promotion.id = :promotionId")
    Long countByPromotionId(@Param("promotionId") Long promotionId);

    @Query("SELECT COUNT(pu) FROM PromotionUsage pu WHERE pu.promotion.id = :promotionId " +
           "AND pu.customer.id = :customerId")
    Long countByPromotionIdAndCustomerId(
            @Param("promotionId") Long promotionId,
            @Param("customerId") Long customerId);

    @Query("SELECT SUM(pu.discountAmount) FROM PromotionUsage pu WHERE pu.promotion.id = :promotionId")
    BigDecimal sumDiscountByPromotionId(@Param("promotionId") Long promotionId);

    @Query("SELECT SUM(pu.discountAmount) FROM PromotionUsage pu " +
           "WHERE pu.promotion.restaurant.id = :restaurantId " +
           "AND pu.usedAt BETWEEN :startDate AND :endDate")
    BigDecimal sumDiscountByRestaurantAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);
}
