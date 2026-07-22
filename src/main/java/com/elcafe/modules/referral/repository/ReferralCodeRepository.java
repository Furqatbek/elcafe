package com.elcafe.modules.referral.repository;

import com.elcafe.modules.referral.entity.ReferralCode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReferralCodeRepository extends JpaRepository<ReferralCode, Long> {

    Optional<ReferralCode> findByCode(String code);

    Optional<ReferralCode> findByRestaurantIdAndCustomerId(Long restaurantId, Long customerId);

    Page<ReferralCode> findByRestaurantId(Long restaurantId, Pageable pageable);

    List<ReferralCode> findByRestaurantIdAndActiveTrue(Long restaurantId);

    boolean existsByCode(String code);

    @Query("SELECT rc FROM ReferralCode rc " +
           "JOIN FETCH rc.customer c " +
           "WHERE rc.restaurant.id = :restaurantId " +
           "ORDER BY rc.usageCount DESC")
    List<ReferralCode> findTopReferrersByRestaurant(@Param("restaurantId") Long restaurantId, Pageable pageable);

    @Query("SELECT COUNT(rc) FROM ReferralCode rc " +
           "WHERE rc.restaurant.id = :restaurantId AND rc.active = true")
    long countActiveByRestaurantId(@Param("restaurantId") Long restaurantId);

    /**
     * Find referral codes for a list of customer IDs (for bulk operations)
     */
    @Query("SELECT rc FROM ReferralCode rc WHERE rc.customer.id IN :customerIds")
    List<ReferralCode> findByCustomerIdIn(@Param("customerIds") java.util.Collection<Long> customerIds);

    /**
     * Find referral code by customer ID
     */
    Optional<ReferralCode> findByCustomerId(Long customerId);
}
