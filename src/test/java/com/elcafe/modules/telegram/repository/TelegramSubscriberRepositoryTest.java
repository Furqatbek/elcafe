package com.elcafe.modules.telegram.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.telegram.entity.TelegramSubscriber;
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

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class TelegramSubscriberRepositoryTest {

    @Autowired private TelegramSubscriberRepository repo;
    @Autowired private EntityManager em;

    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        Customer customer = Customer.builder().restaurantId(1L)
                .firstName("Test").lastName("User").phone("+998901111111").build();
        em.persist(customer);

        // sub1: active, not blocked, recent interaction, linked to customer, REGISTERED
        em.persist(TelegramSubscriber.builder()
                .telegramUserId(1001L)
                .username("alice_bot")
                .firstName("Alice")
                .lastName("Wonder")
                .phone("+998902222222")
                .isActive(true)
                .isBlocked(false)
                .conversationState("REGISTERED")
                .customer(customer)
                .subscribedAt(NOW.minusDays(30))
                .lastInteractionAt(NOW.minusHours(1))
                .build());

        // sub2: active, not blocked, old interaction, no customer, REGISTERED
        em.persist(TelegramSubscriber.builder()
                .telegramUserId(1002L)
                .username("bob_bot")
                .firstName("Bob")
                .lastName("Builder")
                .isActive(true)
                .isBlocked(false)
                .conversationState("REGISTERED")
                .subscribedAt(NOW.minusDays(60))
                .lastInteractionAt(NOW.minusDays(45))
                .build());

        // sub3: blocked
        em.persist(TelegramSubscriber.builder()
                .telegramUserId(1003L)
                .username("charlie_bot")
                .firstName("Charlie")
                .lastName("Blocked")
                .isActive(true)
                .isBlocked(true)
                .conversationState("REGISTERED")
                .subscribedAt(NOW.minusDays(10))
                .lastInteractionAt(NOW.minusHours(2))
                .build());

        em.flush();
        em.clear();
    }

    @Test @DisplayName("findActiveSubscribers — active, not blocked, recent interaction")
    void findActiveSubscribers() {
        OffsetDateTime since = NOW.minusDays(7);
        List<TelegramSubscriber> result = repo.findActiveSubscribers(since);

        assertEquals(1, result.size());
        assertEquals(1001L, result.get(0).getTelegramUserId());
    }

    @Test @DisplayName("findInactiveSubscribers — active, not blocked, old or null interaction")
    void findInactiveSubscribers() {
        OffsetDateTime before = NOW.minusDays(7);
        List<TelegramSubscriber> result = repo.findInactiveSubscribers(before);

        assertEquals(1, result.size());
        assertEquals(1002L, result.get(0).getTelegramUserId());
    }

    @Test @DisplayName("findTargetableSubscribers — active, not blocked, registered")
    void findTargetableSubscribers() {
        List<TelegramSubscriber> result = repo.findTargetableSubscribers();

        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(s -> s.getTelegramUserId().equals(1003L)));
    }

    @Test @DisplayName("findTargetableActiveSubscribers — targetable with recent interaction")
    void findTargetableActiveSubscribers() {
        OffsetDateTime since = NOW.minusDays(7);
        List<TelegramSubscriber> result = repo.findTargetableActiveSubscribers(since);

        assertEquals(1, result.size());
        assertEquals(1001L, result.get(0).getTelegramUserId());
    }

    @Test @DisplayName("findTargetableInactiveSubscribers — targetable with old/null interaction")
    void findTargetableInactiveSubscribers() {
        OffsetDateTime before = NOW.minusDays(7);
        List<TelegramSubscriber> result = repo.findTargetableInactiveSubscribers(before);

        assertEquals(1, result.size());
        assertEquals(1002L, result.get(0).getTelegramUserId());
    }

    @Test @DisplayName("findTargetableLinkedSubscribers — targetable with customer linked")
    void findTargetableLinkedSubscribers() {
        List<TelegramSubscriber> result = repo.findTargetableLinkedSubscribers();

        assertEquals(1, result.size());
        assertEquals(1001L, result.get(0).getTelegramUserId());
    }

    @Test @DisplayName("countActiveSubscribers — active and not blocked")
    void countActiveSubscribers() {
        long count = repo.countActiveSubscribers();

        assertEquals(2, count);
    }

    @Test @DisplayName("countNewSubscribersSince — subscribedAt >= since")
    void countNewSubscribersSince() {
        OffsetDateTime since = NOW.minusDays(15);
        long count = repo.countNewSubscribersSince(since);

        assertEquals(1, count);
    }

    @Test @DisplayName("searchSubscribers — LIKE on username, firstName, lastName")
    void searchSubscribers() {
        Page<TelegramSubscriber> byUsername = repo.searchSubscribers("alice", PageRequest.of(0, 10));
        assertEquals(1, byUsername.getTotalElements());
        assertEquals(1001L, byUsername.getContent().get(0).getTelegramUserId());

        Page<TelegramSubscriber> byFirstName = repo.searchSubscribers("Bob", PageRequest.of(0, 10));
        assertEquals(1, byFirstName.getTotalElements());

        Page<TelegramSubscriber> byLastName = repo.searchSubscribers("Blocked", PageRequest.of(0, 10));
        assertEquals(1, byLastName.getTotalElements());

        Page<TelegramSubscriber> noMatch = repo.searchSubscribers("zzz_nothing", PageRequest.of(0, 10));
        assertEquals(0, noMatch.getTotalElements());
    }
}
