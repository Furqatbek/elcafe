package com.elcafe.modules.promotion.repository;

import com.elcafe.modules.promotion.entity.Promotion;
import com.elcafe.modules.promotion.enums.PromotionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    List<Promotion> findByRestaurant_IdOrderByPriorityDescCreatedAtDesc(Long restaurantId);

    Page<Promotion> findByRestaurant_IdOrderByPriorityDescCreatedAtDesc(Long restaurantId, Pageable pageable);

    @Query("SELECT p FROM Promotion p WHERE p.restaurant.id = :restaurantId " +
           "AND p.active = true " +
           "AND p.startDate <= :now " +
           "AND (p.endDate IS NULL OR p.endDate >= :now) " +
           "ORDER BY p.priority DESC")
    List<Promotion> findActivePromotions(
            @Param("restaurantId") Long restaurantId,
            @Param("now") LocalDateTime now);

    @Query("SELECT p FROM Promotion p WHERE p.restaurant.id = :restaurantId " +
           "AND p.active = true " +
           "AND p.promotionType = :type " +
           "AND p.startDate <= :now " +
           "AND (p.endDate IS NULL OR p.endDate >= :now) " +
           "ORDER BY p.priority DESC")
    List<Promotion> findActivePromotionsByType(
            @Param("restaurantId") Long restaurantId,
            @Param("type") PromotionType type,
            @Param("now") LocalDateTime now);

    @Query("SELECT p FROM Promotion p LEFT JOIN FETCH p.rule LEFT JOIN FETCH p.promotionProducts " +
           "WHERE p.id = :id")
    Promotion findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT COUNT(pu) FROM PromotionUsage pu WHERE pu.promotion.id = :promotionId")
    Long countUsageByPromotionId(@Param("promotionId") Long promotionId);

    @Query("SELECT COUNT(pu) FROM PromotionUsage pu WHERE pu.promotion.id = :promotionId AND pu.customer.id = :customerId")
    Long countUsageByPromotionIdAndCustomerId(
            @Param("promotionId") Long promotionId,
            @Param("customerId") Long customerId);

    boolean existsByRestaurant_IdAndNameIgnoreCase(Long restaurantId, String name);

    /**
     * Ownership check for a promotion id. Used by the Instagram private-reply coupon mint
     * ({@code InstagramWebhookService}) to confirm a bot config's configured promotion actually
     * belongs to that same restaurant before minting a code against it — the {@code restaurantFilter}
     * Hibernate filter alone only actively restricts rows in ENFORCE mode
     * ({@code app.security.tenant-enforcement.mode}, "shadow" by default), so this check is what holds
     * a cross-tenant promotion id closed under the current default too.
     */
    boolean existsByIdAndRestaurant_Id(Long id, Long restaurantId);
}
