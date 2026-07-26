package com.elcafe.modules.instagram.repository;

import com.elcafe.modules.instagram.entity.InstagramLog;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.sms.enums.MessageStatus;
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
 * Mirrors {@link com.elcafe.modules.telegram.repository.TelegramLogRepository}, tenant-scoped: Instagram
 * is per-tenant from birth (V163), so — unlike Telegram's {@code getStatusCountsSince}, which has no
 * restaurant predicate at all — every finder here takes an explicit {@code restaurantId} rather than
 * relying solely on the Hibernate {@code restaurantFilter} being enabled.
 *
 * <p>Telegram has no {@code @Modifying} bulk delete-older-than; it only ever reserved {@code
 * findByCreatedAtBefore} for a retention job that was never built. This mirrors that same, narrower,
 * state rather than inventing a bulk-delete this foundation was not asked to add.
 */
@Repository
public interface InstagramLogRepository extends JpaRepository<InstagramLog, Long> {

    Page<InstagramLog> findByRestaurantIdOrderByCreatedAtDesc(Long restaurantId, Pageable pageable);

    List<InstagramLog> findBySubscriberIdOrderByCreatedAtDesc(Long subscriberId);

    List<InstagramLog> findByCampaignIdOrderByCreatedAtDesc(Long campaignId);

    long countByRestaurantIdAndStatus(Long restaurantId, MessageStatus status);

    @Query("SELECT l.status, COUNT(l) FROM InstagramLog l WHERE l.restaurantId = :restaurantId GROUP BY l.status")
    List<Object[]> getStatusCountsByRestaurantId(@Param("restaurantId") Long restaurantId);

    /** Correlates a future delivery-receipt webhook back to the row it confirms (see entity javadoc). */
    Optional<InstagramLog> findByInstagramMessageId(String instagramMessageId);

    /** Reserved for a future retention job, mirroring TelegramLogRepository's identical reservation. */
    List<InstagramLog> findByCreatedAtBefore(OffsetDateTime before);

    /**
     * Erase every log row carrying a person's igsid within one tenant — the primary PII-erasure path.
     * The wizard, campaign and auto-reply loggers deliberately record a null subscriber (to avoid a lazy
     * fetch on the {@code @Async} send thread), so a person's DM history is reachable only by the
     * denormalised igsid every one of their rows carries — {@link #deleteBySubscriber} alone would miss
     * all but the admin DMs.
     */
    void deleteByRestaurantIdAndIgsid(Long restaurantId, String igsid);

    /** Erase a subscriber's message logs (called when the subscriber itself is deleted — PII erasure). */
    void deleteBySubscriber(InstagramSubscriber subscriber);
}
