package com.elcafe.modules.promotion.repository;

import com.elcafe.modules.promotion.entity.CouponCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CouponCodeRepository extends JpaRepository<CouponCode, Long> {

    Optional<CouponCode> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<CouponCode> findByPromotion_Id(Long promotionId);

    Page<CouponCode> findByPromotion_IdOrderByCreatedAtDesc(Long promotionId, Pageable pageable);

    @Query("SELECT c FROM CouponCode c WHERE c.promotion.restaurant.id = :restaurantId " +
           "ORDER BY c.createdAt DESC")
    Page<CouponCode> findByRestaurantId(@Param("restaurantId") Long restaurantId, Pageable pageable);

    @Query("SELECT c FROM CouponCode c WHERE c.code = :code " +
           "AND c.active = true " +
           "AND (c.validFrom IS NULL OR c.validFrom <= :now) " +
           "AND (c.validUntil IS NULL OR c.validUntil >= :now) " +
           "AND (c.singleUse = false OR c.usedCount = 0) " +
           "AND (c.maxUses IS NULL OR c.usedCount < c.maxUses)")
    Optional<CouponCode> findValidCoupon(@Param("code") String code, @Param("now") LocalDateTime now);

    @Query("SELECT c FROM CouponCode c WHERE c.assignedCustomer.id = :customerId " +
           "AND c.active = true " +
           "ORDER BY c.createdAt DESC")
    List<CouponCode> findActiveByCustomerId(@Param("customerId") Long customerId);

    @Query("SELECT COUNT(c) FROM CouponCode c WHERE c.promotion.id = :promotionId")
    Long countByPromotionId(@Param("promotionId") Long promotionId);
}
