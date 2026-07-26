package com.elcafe.modules.instagram.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.instagram.entity.InstagramInboundMessage;
import com.elcafe.modules.instagram.entity.InstagramSubscriber;
import jakarta.persistence.EntityManager;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * V179: {@code instagram_inbound_message} is per-tenant from birth, like every Instagram table since
 * V163. Each test that scopes by restaurant seeds a decoy row under a SECOND restaurant that would
 * match the query if the scoping were missing — mirrors {@code InstagramLogRepositoryTest}'s pattern
 * exactly, including the dual by-association / by-igsid erasure proof (V171 established that shape;
 * this table reuses it because a message can be recorded before any subscriber row exists yet — see
 * the entity javadoc).
 *
 * <p>Unlike {@code InstagramLogRepositoryTest}'s {@code createdAt} (an {@code @CreatedDate}, stamped by
 * Hibernate auditing and therefore not asserted in exact order to avoid back-to-back-write flakiness),
 * {@code receivedAt} here is a plain application-set column — every test below picks its own explicit
 * values, so ordering assertions are exact and deterministic, not just "scoping held".
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class InstagramInboundMessageRepositoryTest {

    private static final Long TENANT = 1L;
    private static final Long OTHER  = 2L;

    @Autowired
    private InstagramInboundMessageRepository repo;

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

    private InstagramInboundMessage persistMessage(Long restaurantId, InstagramSubscriber subscriber,
                                                    String igsid, String text, OffsetDateTime receivedAt) {
        InstagramInboundMessage msg = InstagramInboundMessage.builder()
                .restaurantId(restaurantId)
                .subscriber(subscriber)
                .igsid(igsid)
                .messageText(text)
                .receivedAt(receivedAt)
                .build();
        em.persist(msg);
        return msg;
    }

    private static OffsetDateTime at(int minutesAgo) {
        return OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(minutesAgo);
    }

    @Test
    @DisplayName("findByRestaurantIdAndSubscriberIdOrderByReceivedAtAsc — only that subscriber's inbound "
            + "history, tenant-scoped, oldest first")
    void findBySubscriber_returnsOnlyThatSubscribersHistoryInOrder() {
        InstagramSubscriber a = persistSubscriber(TENANT, "sub-a");
        InstagramSubscriber b = persistSubscriber(TENANT, "sub-b");
        persistMessage(TENANT, a, "sub-a", "third", at(5));
        persistMessage(TENANT, a, "sub-a", "first", at(30));
        persistMessage(TENANT, a, "sub-a", "second", at(15));
        persistMessage(TENANT, b, "sub-b", "not mine", at(10));
        em.flush();
        em.clear();

        List<InstagramInboundMessage> results = repo
                .findByRestaurantIdAndSubscriberIdOrderByReceivedAtAsc(TENANT, a.getId());

        assertEquals(3, results.size());
        assertEquals(List.of("first", "second", "third"),
                results.stream().map(InstagramInboundMessage::getMessageText).toList());
    }

    @Test
    @DisplayName("findByRestaurantIdAndSubscriberIdOrderByReceivedAtAsc — a foreign restaurantId sees nothing")
    void findBySubscriber_isTenantScoped() {
        InstagramSubscriber a = persistSubscriber(TENANT, "sub-a");
        persistMessage(TENANT, a, "sub-a", "hello", at(1));
        em.flush();
        em.clear();

        assertTrue(repo.findByRestaurantIdAndSubscriberIdOrderByReceivedAtAsc(OTHER, a.getId()).isEmpty());
    }

    @Test
    @DisplayName("findByRestaurantIdAndSubscriberIdInOrderByReceivedAtDesc — newest first, across several "
            + "subscribers, tenant-scoped")
    void findBySubscriberIdIn_returnsNewestFirstAcrossSubscribers() {
        InstagramSubscriber a = persistSubscriber(TENANT, "sub-a");
        InstagramSubscriber b = persistSubscriber(TENANT, "sub-b");
        InstagramSubscriber decoySub = persistSubscriber(OTHER, "sub-other");
        persistMessage(TENANT, a, "sub-a", "a-old", at(60));
        persistMessage(TENANT, a, "sub-a", "a-new", at(5));
        persistMessage(TENANT, b, "sub-b", "b-only", at(20));
        persistMessage(OTHER, decoySub, "sub-other", "decoy", at(1));
        em.flush();
        em.clear();

        List<InstagramInboundMessage> results = repo.findByRestaurantIdAndSubscriberIdInOrderByReceivedAtDesc(
                TENANT, List.of(a.getId(), b.getId()));

        assertEquals(3, results.size(), "only the two TENANT subscribers' rows, not the OTHER-tenant decoy");
        assertEquals(List.of("a-new", "b-only", "a-old"),
                results.stream().map(InstagramInboundMessage::getMessageText).toList());
    }

    @Test
    @DisplayName("findRecentConversationSubscriberIds — most-recently-messaged subscriber first, tenant-scoped")
    void findRecentConversationSubscriberIds_ordersByMostRecentAndScopesToTenant() {
        InstagramSubscriber stale = persistSubscriber(TENANT, "stale-sub");
        InstagramSubscriber fresh = persistSubscriber(TENANT, "fresh-sub");
        InstagramSubscriber other = persistSubscriber(OTHER, "other-sub");
        persistMessage(TENANT, stale, "stale-sub", "old message", at(120));
        persistMessage(TENANT, fresh, "fresh-sub", "brand new", at(1));
        // A second, OLDER message for "stale" must not make it look more recent than its own latest.
        persistMessage(TENANT, stale, "stale-sub", "even older", at(200));
        persistMessage(OTHER, other, "other-sub", "decoy tenant", at(0));
        em.flush();
        em.clear();

        Page<Long> page = repo.findRecentConversationSubscriberIds(TENANT, PageRequest.of(0, 10));

        assertEquals(2, page.getTotalElements(), "two TENANT subscribers with inbound history, not the decoy");
        assertEquals(List.of(fresh.getId(), stale.getId()), page.getContent(),
                "fresh (1 min ago) ranks above stale (last message 120 min ago), by MAX(receivedAt)");
    }

    @Test
    @DisplayName("findRecentConversationSubscriberIds — a message with no subscriber yet has no conversation to join")
    void findRecentConversationSubscriberIds_excludesNullSubscriberRows() {
        InstagramSubscriber withThread = persistSubscriber(TENANT, "has-thread");
        persistMessage(TENANT, withThread, "has-thread", "hi", at(5));
        // A stranger's first-ever message was a STOP/SUBSCRIBE keyword — InstagramBotService never
        // created a subscriber row for it (see InstagramInboundMessage's javadoc).
        persistMessage(TENANT, null, "stranger", "STOP", at(1));
        em.flush();
        em.clear();

        Page<Long> page = repo.findRecentConversationSubscriberIds(TENANT, PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals(withThread.getId(), page.getContent().get(0));
    }

    @Test
    @DisplayName("deleteBySubscriber — erases only that subscriber's inbound history (PII erasure path)")
    void deleteBySubscriber_erasesOnlyThatSubscribersHistory() {
        InstagramSubscriber toErase = persistSubscriber(TENANT, "erase-me");
        InstagramSubscriber untouched = persistSubscriber(TENANT, "keep-me");
        persistMessage(TENANT, toErase, "erase-me", "will go", at(5));
        persistMessage(TENANT, toErase, "erase-me", "will also go", at(2));
        persistMessage(TENANT, untouched, "keep-me", "stays", at(3));
        em.flush();
        em.clear();

        InstagramSubscriber managedToErase = em.find(InstagramSubscriber.class, toErase.getId());
        repo.deleteBySubscriber(managedToErase);
        em.flush();
        em.clear();

        assertTrue(repo.findByRestaurantIdAndSubscriberIdOrderByReceivedAtAsc(TENANT, toErase.getId()).isEmpty());
        assertEquals(1, repo.findByRestaurantIdAndSubscriberIdOrderByReceivedAtAsc(TENANT, untouched.getId()).size());
    }

    @Test
    @DisplayName("deleteByRestaurantIdAndIgsid — erases every row for that igsid incl. null-subscriber rows, "
            + "tenant-scoped")
    void deleteByRestaurantIdAndIgsid_erasesEveryRowForThatIgsid() {
        // Recorded before any subscriber existed (a stranger's STOP) — only reachable by igsid.
        persistMessage(TENANT, null, "person-1", "STOP", at(50));
        InstagramSubscriber linked = persistSubscriber(TENANT, "person-1");
        persistMessage(TENANT, linked, "person-1", "hi again", at(10));
        // Must survive: a different person in this tenant, and the SAME igsid under another tenant.
        persistMessage(TENANT, null, "person-2", "hello", at(1));
        InstagramSubscriber otherTenantSameIgsid = persistSubscriber(OTHER, "person-1");
        persistMessage(OTHER, otherTenantSameIgsid, "person-1", "different tenant", at(1));
        em.flush();
        em.clear();

        repo.deleteByRestaurantIdAndIgsid(TENANT, "person-1");
        em.flush();
        em.clear();

        List<InstagramInboundMessage> tenantRemaining = repo
                .findByRestaurantIdAndSubscriberIdInOrderByReceivedAtDesc(TENANT,
                        List.of(linked.getId()));
        assertTrue(tenantRemaining.isEmpty(), "both person-1 rows under TENANT are gone");

        Page<Long> tenantConversations = repo.findRecentConversationSubscriberIds(TENANT, PageRequest.of(0, 10));
        assertEquals(0, tenantConversations.getTotalElements(),
                "person-1's subscriber row still exists but has no inbound rows left to group");

        List<InstagramInboundMessage> otherTenantRows = repo
                .findByRestaurantIdAndSubscriberIdOrderByReceivedAtAsc(OTHER, otherTenantSameIgsid.getId());
        assertEquals(1, otherTenantRows.size(), "the same igsid under another tenant is untouched");
    }
}
