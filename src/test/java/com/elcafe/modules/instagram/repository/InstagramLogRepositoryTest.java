package com.elcafe.modules.instagram.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.instagram.entity.InstagramLog;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import com.elcafe.modules.instagram.enums.InstagramMessageType;
import com.elcafe.modules.sms.enums.MessageStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V171: {@code instagram_logs} is per-tenant from birth. Each test that scopes by restaurant seeds a
 * decoy row under a SECOND restaurant that would match the query if the scoping were missing — so these
 * assertions fail loudly if the restaurant predicate is ever dropped, rather than silently passing on a
 * single-tenant fixture. Mirrors {@code InstagramSubscriberRepositoryTest}'s pattern.
 *
 * <p>Ordering ({@code OrderByCreatedAtDesc}) is exercised for scoping/filtering correctness only, not for
 * exact row order — matching this codebase's existing convention (e.g.
 * {@code CourierWalletTransactionRepositoryTest}) of not asserting timestamp ordering created within the
 * same test, which back-to-back {@code @CreatedDate} writes can make flaky.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class InstagramLogRepositoryTest {

    private static final Long TENANT = 1L;
    private static final Long OTHER  = 2L;

    @Autowired
    private InstagramLogRepository repo;

    @Autowired
    private EntityManager em;

    private InstagramSubscriber persistSubscriber(Long restaurantId, String igsid) {
        InstagramSubscriber sub = InstagramSubscriber.builder()
                .restaurantId(restaurantId)
                .igsid(igsid)
                .isActive(true)
                .isBlocked(false)
                .build();
        em.persist(sub);
        return sub;
    }

    private InstagramLog persistLog(Long restaurantId, InstagramSubscriber subscriber, String igsid,
                                    InstagramMessageType type, MessageStatus status, Long campaignId,
                                    String instagramMessageId) {
        InstagramLog logEntry = InstagramLog.builder()
                .restaurantId(restaurantId)
                .subscriber(subscriber)
                .igsid(igsid)
                .messageType(type)
                .status(status)
                .campaignId(campaignId)
                .instagramMessageId(instagramMessageId)
                .message("hello")
                .build();
        em.persist(logEntry);
        return logEntry;
    }

    /** Same shape as a matching row, but owned by another restaurant. */
    private void decoy(MessageStatus status) {
        persistLog(OTHER, null, "ig-other", InstagramMessageType.MANUAL, status, null, null);
    }

    @Test
    @DisplayName("findByRestaurantIdOrderByCreatedAtDesc — only this tenant's rows")
    void findByRestaurantId_isTenantScoped() {
        persistLog(TENANT, null, "ig1", InstagramMessageType.AUTOMATION, MessageStatus.SENT, null, null);
        persistLog(TENANT, null, "ig2", InstagramMessageType.MANUAL, MessageStatus.FAILED, null, null);
        decoy(MessageStatus.SENT);
        em.flush();
        em.clear();

        Page<InstagramLog> page = repo.findByRestaurantIdOrderByCreatedAtDesc(TENANT, PageRequest.of(0, 10));

        assertEquals(2, page.getTotalElements());
        assertTrue(page.getContent().stream().allMatch(l -> l.getRestaurantId().equals(TENANT)));
    }

    @Test
    @DisplayName("findBySubscriberIdOrderByCreatedAtDesc — only that subscriber's logs, not another's")
    void findBySubscriberId_returnsOnlyThatSubscribersLogs() {
        InstagramSubscriber a = persistSubscriber(TENANT, "sub-a");
        InstagramSubscriber b = persistSubscriber(TENANT, "sub-b");
        persistLog(TENANT, a, "sub-a", InstagramMessageType.AUTOMATION, MessageStatus.SENT, null, null);
        persistLog(TENANT, a, "sub-a", InstagramMessageType.MANUAL, MessageStatus.SENT, null, null);
        persistLog(TENANT, b, "sub-b", InstagramMessageType.MANUAL, MessageStatus.SENT, null, null);
        em.flush();
        em.clear();

        List<InstagramLog> results = repo.findBySubscriberIdOrderByCreatedAtDesc(a.getId());

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(l -> l.getSubscriber().getId().equals(a.getId())));
    }

    @Test
    @DisplayName("findByCampaignIdOrderByCreatedAtDesc — only that campaign's send log")
    void findByCampaignId_returnsOnlyThatCampaignsLogs() {
        persistLog(TENANT, null, "ig1", InstagramMessageType.CAMPAIGN, MessageStatus.SENT, 100L, null);
        persistLog(TENANT, null, "ig2", InstagramMessageType.CAMPAIGN, MessageStatus.SENT, 100L, null);
        persistLog(TENANT, null, "ig3", InstagramMessageType.CAMPAIGN, MessageStatus.SENT, 200L, null);
        persistLog(TENANT, null, "ig4", InstagramMessageType.MANUAL, MessageStatus.SENT, null, null); // no campaign
        em.flush();
        em.clear();

        List<InstagramLog> results = repo.findByCampaignIdOrderByCreatedAtDesc(100L);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(l -> l.getCampaignId().equals(100L)));
    }

    @Test
    @DisplayName("countByRestaurantIdAndStatus — tenant- and status-scoped")
    void countByRestaurantIdAndStatus_isTenantAndStatusScoped() {
        persistLog(TENANT, null, "ig1", InstagramMessageType.MANUAL, MessageStatus.SENT, null, null);
        persistLog(TENANT, null, "ig2", InstagramMessageType.MANUAL, MessageStatus.SENT, null, null);
        persistLog(TENANT, null, "ig3", InstagramMessageType.MANUAL, MessageStatus.FAILED, null, null);
        // Same status, other tenant — would inflate the count if the restaurant predicate were dropped.
        decoy(MessageStatus.SENT);
        decoy(MessageStatus.SENT);
        decoy(MessageStatus.SENT);
        decoy(MessageStatus.SENT);
        decoy(MessageStatus.SENT);
        em.flush();
        em.clear();

        assertEquals(2, repo.countByRestaurantIdAndStatus(TENANT, MessageStatus.SENT));
        assertEquals(1, repo.countByRestaurantIdAndStatus(TENANT, MessageStatus.FAILED));
        assertEquals(0, repo.countByRestaurantIdAndStatus(TENANT, MessageStatus.DELIVERED));
        assertEquals(5, repo.countByRestaurantIdAndStatus(OTHER, MessageStatus.SENT));
    }

    @Test
    @DisplayName("getStatusCountsByRestaurantId — grouped counts stay within this tenant")
    void getStatusCountsByRestaurantId_isTenantScoped() {
        persistLog(TENANT, null, "ig1", InstagramMessageType.MANUAL, MessageStatus.SENT, null, null);
        persistLog(TENANT, null, "ig2", InstagramMessageType.MANUAL, MessageStatus.SENT, null, null);
        persistLog(TENANT, null, "ig3", InstagramMessageType.MANUAL, MessageStatus.FAILED, null, null);
        for (int i = 0; i < 10; i++) {
            decoy(MessageStatus.SENT); // would dominate the SENT bucket if unscoped
        }
        em.flush();
        em.clear();

        List<Object[]> counts = repo.getStatusCountsByRestaurantId(TENANT);

        long total = counts.stream().mapToLong(row -> (Long) row[1]).sum();
        assertEquals(3, total, "only this tenant's 3 rows, none of the 10 decoys");
        for (Object[] row : counts) {
            MessageStatus status = (MessageStatus) row[0];
            long count = (Long) row[1];
            if (status == MessageStatus.SENT) assertEquals(2, count);
            if (status == MessageStatus.FAILED) assertEquals(1, count);
        }
    }

    @Test
    @DisplayName("findByInstagramMessageId — exact mid lookup, the future delivery-receipt correlation hook")
    void findByInstagramMessageId_findsTheExactRow() {
        persistLog(TENANT, null, "ig1", InstagramMessageType.MANUAL, MessageStatus.SENT, null, "mid-123");
        em.flush();
        em.clear();

        Optional<InstagramLog> found = repo.findByInstagramMessageId("mid-123");
        assertTrue(found.isPresent());
        assertEquals("ig1", found.get().getIgsid());

        assertTrue(repo.findByInstagramMessageId("mid-does-not-exist").isEmpty());
    }

    @Test
    @DisplayName("findByCreatedAtBefore — the reserved retention-job finder, mirroring TelegramLogRepository")
    void findByCreatedAtBefore_filtersOnBackdatedRows() {
        InstagramLog recent = persistLog(TENANT, null, "ig-recent", InstagramMessageType.MANUAL, MessageStatus.SENT, null, null);
        InstagramLog old = persistLog(TENANT, null, "ig-old", InstagramMessageType.MANUAL, MessageStatus.SENT, null, null);
        em.flush();

        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusDays(30);
        em.createNativeQuery("UPDATE instagram_logs SET created_at = :past WHERE id = :id")
                .setParameter("past", cutoff.minusDays(1))
                .setParameter("id", old.getId())
                .executeUpdate();
        em.flush();
        em.clear();

        List<InstagramLog> results = repo.findByCreatedAtBefore(cutoff);

        assertEquals(1, results.size());
        assertEquals(old.getId(), results.get(0).getId());
        assertNotEquals(recent.getId(), results.get(0).getId());
    }

    @Test
    @DisplayName("deleteBySubscriber — erases only that subscriber's logs (PII erasure path)")
    void deleteBySubscriber_erasesOnlyThatSubscribersLogs() {
        InstagramSubscriber toErase = persistSubscriber(TENANT, "erase-me");
        InstagramSubscriber untouched = persistSubscriber(TENANT, "keep-me");
        persistLog(TENANT, toErase, "erase-me", InstagramMessageType.AUTOMATION, MessageStatus.SENT, null, null);
        persistLog(TENANT, toErase, "erase-me", InstagramMessageType.MANUAL, MessageStatus.SENT, null, null);
        persistLog(TENANT, untouched, "keep-me", InstagramMessageType.MANUAL, MessageStatus.SENT, null, null);
        em.flush();
        em.clear();

        InstagramSubscriber managedToErase = em.find(InstagramSubscriber.class, toErase.getId());
        repo.deleteBySubscriber(managedToErase);
        em.flush();
        em.clear();

        assertTrue(repo.findBySubscriberIdOrderByCreatedAtDesc(toErase.getId()).isEmpty());
        assertEquals(1, repo.findBySubscriberIdOrderByCreatedAtDesc(untouched.getId()).size());
    }

    @Test
    @DisplayName("metadata (jsonb) round-trips a Map, mirroring TelegramLog's JdbcTypeCode(SqlTypes.JSON) approach")
    void metadata_roundTripsAsAMap() {
        InstagramLog logEntry = InstagramLog.builder()
                .restaurantId(TENANT)
                .igsid("ig1")
                .messageType(InstagramMessageType.CAMPAIGN)
                .status(MessageStatus.SENT)
                .metadata(Map.of("attempt", 1, "source", "campaign-executor"))
                .build();
        em.persist(logEntry);
        em.flush();
        em.clear();

        InstagramLog reloaded = em.find(InstagramLog.class, logEntry.getId());
        assertNotNull(reloaded.getMetadata());
        assertEquals("campaign-executor", reloaded.getMetadata().get("source"));
    }
}
