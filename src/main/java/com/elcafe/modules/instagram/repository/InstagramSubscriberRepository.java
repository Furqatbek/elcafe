package com.elcafe.modules.instagram.repository;

import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * All finders are explicitly tenant-scoped (V163). The §3.4 {@code restaurantFilter} is the backstop,
 * but it is only enabled on request-bound sessions — the webhook processes events on an {@code @Async}
 * thread with no request, so scoping there MUST come from the query itself.
 */
@Repository
public interface InstagramSubscriberRepository extends JpaRepository<InstagramSubscriber, Long> {

    /** Webhook path: resolve the sender within the restaurant that owns the receiving IG account. */
    Optional<InstagramSubscriber> findByIgsidAndRestaurantId(String igsid, Long restaurantId);

    /** Tenant-scoped by-id lookup — closes the IDOR that a bare {@code findById} leaves open. */
    Optional<InstagramSubscriber> findByIdAndRestaurantId(Long id, Long restaurantId);

    Page<InstagramSubscriber> findByRestaurantIdAndIsActiveTrue(Long restaurantId, Pageable pageable);

    /** Customer-linked lookup, e.g. to reach a subscriber for order notifications. */
    List<InstagramSubscriber> findByRestaurantIdAndCustomerId(Long restaurantId, Long customerId);

    @Query("SELECT COUNT(s) FROM InstagramSubscriber s WHERE s.restaurantId = :restaurantId " +
           "AND s.isActive = true AND s.isBlocked = false AND s.conversationState = 'REGISTERED'")
    long countRegistered(@Param("restaurantId") Long restaurantId);

    @Query("SELECT s FROM InstagramSubscriber s WHERE s.restaurantId = :restaurantId AND (" +
           "LOWER(s.username) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(s.displayName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "s.phone LIKE CONCAT('%', :q, '%'))")
    Page<InstagramSubscriber> search(@Param("restaurantId") Long restaurantId,
                                     @Param("q") String query,
                                     Pageable pageable);

    /** All active, non-blocked subscribers of one tenant (used for broadcast). */
    @Query("SELECT s FROM InstagramSubscriber s WHERE s.restaurantId = :restaurantId " +
           "AND s.isActive = true AND s.isBlocked = false")
    List<InstagramSubscriber> findAllActiveNotBlocked(@Param("restaurantId") Long restaurantId);

    /** Active, non-blocked, fully registered subscribers of one tenant (targeted broadcast). */
    @Query("SELECT s FROM InstagramSubscriber s WHERE s.restaurantId = :restaurantId " +
           "AND s.isActive = true AND s.isBlocked = false AND s.conversationState = 'REGISTERED'")
    List<InstagramSubscriber> findAllRegistered(@Param("restaurantId") Long restaurantId);
}
