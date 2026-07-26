package com.elcafe.modules.instagram.repository;

import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
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

    /** Every subscriber linked to a customer — used to erase their PII when the customer is deleted. */
    List<InstagramSubscriber> findByCustomerId(Long customerId);

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

    /**
     * Active, non-blocked subscribers of one tenant who are still inside Instagram's messaging window —
     * a campaign may only DM a user within ~24h of their last inbound message. {@code touch()} bumps
     * {@code lastInteractionAt} on every inbound event, so it is the window proxy; {@code since} is
     * {@code now - windowHours}. A row with a null {@code lastInteractionAt} (no recorded interaction)
     * is excluded, which is the safe default. Filtering here keeps a campaign from firing sends Meta
     * rejects with code 10 — sustained, the thing that gets an app restricted.
     */
    @Query("SELECT s FROM InstagramSubscriber s WHERE s.restaurantId = :restaurantId " +
           "AND s.isActive = true AND s.isBlocked = false AND s.lastInteractionAt >= :since")
    List<InstagramSubscriber> findAllActiveNotBlockedSince(@Param("restaurantId") Long restaurantId,
                                                           @Param("since") OffsetDateTime since);

    /** As {@link #findAllActiveNotBlockedSince} but only fully-registered subscribers. */
    @Query("SELECT s FROM InstagramSubscriber s WHERE s.restaurantId = :restaurantId " +
           "AND s.isActive = true AND s.isBlocked = false AND s.conversationState = 'REGISTERED' " +
           "AND s.lastInteractionAt >= :since")
    List<InstagramSubscriber> findAllRegisteredSince(@Param("restaurantId") Long restaurantId,
                                                     @Param("since") OffsetDateTime since);
}
