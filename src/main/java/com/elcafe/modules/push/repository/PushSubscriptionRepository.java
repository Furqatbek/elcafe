package com.elcafe.modules.push.repository;

import com.elcafe.modules.push.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    /**
     * Find subscription by endpoint
     */
    Optional<PushSubscription> findByEndpoint(String endpoint);

    /**
     * Find all active subscriptions for a customer
     */
    List<PushSubscription> findByCustomerIdAndIsActiveTrue(Long customerId);

    /**
     * Find all active subscriptions for a user (admin)
     */
    List<PushSubscription> findByUserIdAndIsActiveTrue(Long userId);

    /**
     * Find all active subscriptions
     */
    List<PushSubscription> findByIsActiveTrue();

    /**
     * Count active subscriptions
     */
    long countByIsActiveTrue();

    /**
     * Count subscriptions by customer
     */
    long countByCustomerIdAndIsActiveTrue(Long customerId);

    /**
     * Deactivate subscription by endpoint
     */
    @Modifying
    @Query("UPDATE PushSubscription p SET p.isActive = false WHERE p.endpoint = :endpoint")
    void deactivateByEndpoint(@Param("endpoint") String endpoint);

    /**
     * Deactivate all subscriptions for customer
     */
    @Modifying
    @Query("UPDATE PushSubscription p SET p.isActive = false WHERE p.customer.id = :customerId")
    void deactivateAllByCustomerId(@Param("customerId") Long customerId);

    /**
     * Find expired subscriptions
     */
    @Query("SELECT p FROM PushSubscription p WHERE p.expiresAt IS NOT NULL AND p.expiresAt < :now AND p.isActive = true")
    List<PushSubscription> findExpiredSubscriptions(@Param("now") LocalDateTime now);

    /**
     * Delete inactive subscriptions older than given date
     */
    @Modifying
    @Query("DELETE FROM PushSubscription p WHERE p.isActive = false AND p.updatedAt < :cutoffDate")
    int deleteInactiveOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Check if endpoint exists
     */
    boolean existsByEndpoint(String endpoint);
}
