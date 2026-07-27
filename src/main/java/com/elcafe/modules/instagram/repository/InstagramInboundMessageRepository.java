package com.elcafe.modules.instagram.repository;

import com.elcafe.modules.instagram.entity.InstagramInboundMessage;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * V179. Mirrors {@link InstagramLogRepository}'s tenant-scoping and dual-erasure conventions: every
 * finder takes an explicit {@code restaurantId} — the webhook that WRITES these rows runs on an
 * {@code @Async} thread with no request-bound {@code restaurantFilter} — and erasure is reachable both
 * by subscriber association and by the denormalised igsid (a row can be recorded before any subscriber
 * exists yet; see the entity javadoc).
 */
@Repository
public interface InstagramInboundMessageRepository extends JpaRepository<InstagramInboundMessage, Long> {

    /** One conversation's inbound history, oldest first — half of the inbox's merged transcript
     *  ({@code InstagramInboxService#getConversation}, merged with that subscriber's outbound {@link
     *  InstagramLog} rows). */
    List<InstagramInboundMessage> findByRestaurantIdAndSubscriberIdOrderByReceivedAtAsc(
            Long restaurantId, Long subscriberId);

    /**
     * The most recent inbound rows across several subscribers in one query (newest first per row, not
     * merged across subscribers) — {@code InstagramInboxService} keeps only the first row encountered
     * per subscriber id as that conversation's preview, avoiding one query per row in the "recent
     * conversations" listing.
     */
    /**
     * Paged variant of the finder below, for the customer-360 timeline (V183): that view merges several
     * channels and only ever needs one window at a time, so it must be able to bound what it pulls
     * rather than loading a guest's whole inbound history to render twenty lines.
     */
    List<InstagramInboundMessage> findByRestaurantIdAndSubscriberIdInOrderByReceivedAtDesc(
            Long restaurantId, List<Long> subscriberIds, org.springframework.data.domain.Pageable pageable);

    List<InstagramInboundMessage> findByRestaurantIdAndSubscriberIdInOrderByReceivedAtDesc(
            Long restaurantId, List<Long> subscriberIds);

    /**
     * Subscriber ids with at least one inbound message, most-recently-messaged first — the "recent
     * conversations" listing's spine. A message recorded with no subscriber yet (see entity javadoc)
     * has nothing to group into here, which is the correct behaviour: there is no conversation thread
     * to open for it until one exists. An explicit {@code countQuery} is supplied since Spring Data's
     * automatic derivation is not reliable for a {@code GROUP BY} projection.
     */
    @Query(value = "SELECT m.subscriber.id FROM InstagramInboundMessage m "
                  + "WHERE m.restaurantId = :restaurantId AND m.subscriber IS NOT NULL "
                  + "GROUP BY m.subscriber.id ORDER BY MAX(m.receivedAt) DESC",
           countQuery = "SELECT COUNT(DISTINCT m.subscriber.id) FROM InstagramInboundMessage m "
                      + "WHERE m.restaurantId = :restaurantId AND m.subscriber IS NOT NULL")
    Page<Long> findRecentConversationSubscriberIds(@Param("restaurantId") Long restaurantId, Pageable pageable);

    /** Erase a subscriber's inbound history (PII erasure) — the FK cascade's explicit counterpart,
     *  called from {@code InstagramMessageLogger#eraseSubscriberLogs}. */
    void deleteBySubscriber(InstagramSubscriber subscriber);

    /** Erase every inbound row for a person's igsid within one tenant, including rows recorded before
     *  any subscriber existed (null subscriber_id) — mirrors {@link
     *  InstagramLogRepository#deleteByRestaurantIdAndIgsid}. */
    void deleteByRestaurantIdAndIgsid(Long restaurantId, String igsid);
}
