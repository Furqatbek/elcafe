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

    // -------------------------------------------------------------------------------------------
    // Statistics counts: every one below backs GET /api/v1/instagram/subscribers/statistics
    // (InstagramStatisticsService), reading the existing table — no migration needed. Each carries its
    // own explicit restaurantId predicate — Instagram is per-tenant from birth (V163), so a decoy row
    // under a second restaurant must never inflate another tenant's numbers.
    // -------------------------------------------------------------------------------------------

    /** Every subscriber of this tenant, in any state — the statistics "total" bucket. */
    long countByRestaurantId(Long restaurantId);

    /** Active subscribers of this tenant, regardless of blocked/conversation state. */
    long countByRestaurantIdAndIsActiveTrue(Long restaurantId);

    /** Blocked subscribers of this tenant, regardless of active state. */
    long countByRestaurantIdAndIsBlockedTrue(Long restaurantId);

    /** Subscribers of this tenant created since {@code since} — backs newThisWeek/newThisMonth. */
    long countByRestaurantIdAndCreatedAtAfter(Long restaurantId, OffsetDateTime since);

    @Query("SELECT s FROM InstagramSubscriber s WHERE s.restaurantId = :restaurantId AND (" +
           "LOWER(s.username) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(s.displayName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "s.phone LIKE CONCAT('%', :q, '%'))")
    Page<InstagramSubscriber> search(@Param("restaurantId") Long restaurantId,
                                     @Param("q") String query,
                                     Pageable pageable);

    /**
     * Active, non-blocked, opted-IN subscribers of one tenant who are still inside Instagram's
     * messaging window — a campaign may only DM a user within ~24h of their last inbound message.
     * {@code touch()} bumps {@code lastInteractionAt} on every inbound event, so it is the window
     * proxy; {@code since} is {@code now - windowHours}. A row with a null {@code lastInteractionAt}
     * (no recorded interaction) is excluded, which is the safe default. Filtering here keeps a
     * campaign from firing sends Meta rejects with code 10 — sustained, the thing that gets an app
     * restricted.
     *
     * <p>V174: {@code marketingOptIn = true} is the same restriction risk from a different angle — a
     * subscriber who typed STOP must never be enqueued into a campaign again, regardless of how
     * recently they interacted. Since {@code marketingOptIn} defaults true (grandfathering existing
     * subscribers — see V174's migration comment), this predicate changes nothing for anyone who has
     * not explicitly opted out; it only ever REMOVES opted-out rows from what was already the ALL
     * audience, so {@code InstagramCampaign.recipientCount} legitimately drops by the opted-out count.
     */
    @Query("SELECT s FROM InstagramSubscriber s WHERE s.restaurantId = :restaurantId " +
           "AND s.isActive = true AND s.isBlocked = false AND s.marketingOptIn = true " +
           "AND s.lastInteractionAt >= :since")
    List<InstagramSubscriber> findAllActiveNotBlockedSince(@Param("restaurantId") Long restaurantId,
                                                           @Param("since") OffsetDateTime since);

    /** As {@link #findAllActiveNotBlockedSince} but only fully-registered subscribers. */
    @Query("SELECT s FROM InstagramSubscriber s WHERE s.restaurantId = :restaurantId " +
           "AND s.isActive = true AND s.isBlocked = false AND s.marketingOptIn = true " +
           "AND s.conversationState = 'REGISTERED' AND s.lastInteractionAt >= :since")
    List<InstagramSubscriber> findAllRegisteredSince(@Param("restaurantId") Long restaurantId,
                                                     @Param("since") OffsetDateTime since);

    // ---------------------------------------------------------------------------------------------
    // V178 (InstagramScheduler — added by the automation-rules/scheduler agent): birthday and win-back
    // targeting. Both stay tenant-scoped and opted-IN for the same reasons findAllActiveNotBlockedSince/
    // findAllRegisteredSince above do, but — unlike those two — do NOT also filter to the 24h messaging
    // window: a scheduled automation job attempts every eligible subscriber and lets Meta's own response
    // (delivered vs RECIPIENT_UNAVAILABLE) be the record of what was actually reachable. See
    // InstagramScheduler's class javadoc for the full 24h-window explanation.
    // ---------------------------------------------------------------------------------------------

    /**
     * Active, non-blocked, opted-in subscribers of one tenant whose recorded birthday (month + day,
     * year-independent) is today. A subscriber with no {@code birthDate} on file (never reached, or
     * skipped, the wizard's AWAITING_BIRTHDAY step) never matches.
     */
    @Query("SELECT s FROM InstagramSubscriber s WHERE s.restaurantId = :restaurantId " +
           "AND s.isActive = true AND s.isBlocked = false AND s.marketingOptIn = true " +
           "AND s.birthDate IS NOT NULL AND MONTH(s.birthDate) = :month AND DAY(s.birthDate) = :day")
    List<InstagramSubscriber> findBirthdaysToday(@Param("restaurantId") Long restaurantId,
                                                 @Param("month") int month, @Param("day") int day);

    /**
     * Active, non-blocked, opted-in subscribers of one tenant who have gone quiet: {@code
     * lastInteractionAt} older than {@code before}, OR never recorded at all — mirrors {@code
     * TelegramSubscriberRepository#findTargetableInactiveSubscribers}. This is the WIN_BACK audience,
     * which by construction is almost always OUTSIDE Instagram's 24h DM window.
     */
    @Query("SELECT s FROM InstagramSubscriber s WHERE s.restaurantId = :restaurantId " +
           "AND s.isActive = true AND s.isBlocked = false AND s.marketingOptIn = true " +
           "AND (s.lastInteractionAt IS NULL OR s.lastInteractionAt < :before)")
    List<InstagramSubscriber> findInactiveSince(@Param("restaurantId") Long restaurantId,
                                                @Param("before") OffsetDateTime before);
}
