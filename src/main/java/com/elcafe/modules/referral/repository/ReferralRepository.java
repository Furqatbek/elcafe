package com.elcafe.modules.referral.repository;

import com.elcafe.modules.referral.entity.Referral;
import com.elcafe.modules.referral.enums.ReferralStatus;
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
public interface ReferralRepository extends JpaRepository<Referral, Long> {

    Page<Referral> findByRestaurantId(Long restaurantId, Pageable pageable);

    List<Referral> findByReferrerId(Long referrerId);

    Optional<Referral> findByRefereeIdAndRestaurantId(Long refereeId, Long restaurantId);

    boolean existsByRefereeIdAndRestaurantId(Long refereeId, Long restaurantId);

    @Query("SELECT r FROM Referral r " +
           "JOIN FETCH r.referrer " +
           "JOIN FETCH r.referee " +
           "WHERE r.restaurant.id = :restaurantId " +
           "ORDER BY r.createdAt DESC")
    Page<Referral> findByRestaurantIdWithDetails(@Param("restaurantId") Long restaurantId, Pageable pageable);

    @Query("SELECT r FROM Referral r " +
           "WHERE r.referrer.id = :customerId " +
           "AND r.restaurant.id = :restaurantId")
    List<Referral> findByReferrerAndRestaurant(@Param("customerId") Long customerId,
                                                @Param("restaurantId") Long restaurantId);

    @Query("SELECT COUNT(r) FROM Referral r " +
           "WHERE r.referrer.id = :referrerId " +
           "AND r.restaurant.id = :restaurantId " +
           "AND r.status = :status")
    long countByReferrerAndRestaurantAndStatus(@Param("referrerId") Long referrerId,
                                               @Param("restaurantId") Long restaurantId,
                                               @Param("status") ReferralStatus status);

    @Query("SELECT COUNT(r) FROM Referral r " +
           "WHERE r.restaurant.id = :restaurantId " +
           "AND r.status = :status")
    long countByRestaurantIdAndStatus(@Param("restaurantId") Long restaurantId,
                                      @Param("status") ReferralStatus status);

    @Query("SELECT r FROM Referral r " +
           "WHERE r.status = 'PENDING' " +
           "AND r.createdAt < :expiryDate")
    List<Referral> findExpiredPendingReferrals(@Param("expiryDate") LocalDateTime expiryDate);

    @Query("SELECT COUNT(r) FROM Referral r " +
           "WHERE r.restaurant.id = :restaurantId")
    long countByRestaurantId(@Param("restaurantId") Long restaurantId);

    @Query("SELECT COUNT(r) FROM Referral r " +
           "WHERE r.restaurant.id = :restaurantId " +
           "AND r.createdAt >= :startDate")
    long countByRestaurantIdAndCreatedAtAfter(@Param("restaurantId") Long restaurantId,
                                               @Param("startDate") LocalDateTime startDate);

    /**
     * Find referrals by referral code customer IDs and status (for bulk operations)
     */
    @Query("SELECT r FROM Referral r WHERE r.referralCode.customer.id IN :customerIds AND r.status = :status")
    List<Referral> findByReferralCodeCustomerIdInAndStatus(
            @Param("customerIds") java.util.Collection<Long> customerIds,
            @Param("status") ReferralStatus status);

    /**
     * Count completed referrals by referral code customer ID
     */
    @Query("SELECT COUNT(r) FROM Referral r WHERE r.referralCode.customer.id = :customerId AND r.status = :status")
    long countByReferralCodeCustomerIdAndStatus(
            @Param("customerId") Long customerId,
            @Param("status") ReferralStatus status);
}
