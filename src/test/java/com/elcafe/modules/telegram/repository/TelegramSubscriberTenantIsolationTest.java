package com.elcafe.modules.telegram.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.telegram.entity.TelegramSubscriber;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * V164 made Telegram a per-tenant channel. The single most important consequence is that
 * {@code telegram_user_id} is no longer globally unique: one Telegram account can subscribe to
 * several restaurants' bots, and each of those is an independent subscriber with its own wizard
 * state, phone and saved locations.
 *
 * <p>Before V164 the second insert below would have violated
 * {@code telegram_subscribers_telegram_user_id_key}, which is exactly why the channel could not be
 * per-tenant. This test fails if that constraint is ever restored globally.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class TelegramSubscriberTenantIsolationTest {

    private static final Long TENANT = 1L;
    private static final Long OTHER  = 2L;

    @Autowired private TelegramSubscriberRepository repo;
    @Autowired private EntityManager em;

    private TelegramSubscriber subscriber(Long restaurantId, Long telegramUserId, String displayName) {
        TelegramSubscriber s = TelegramSubscriber.builder()
                .restaurantId(restaurantId)
                .telegramUserId(telegramUserId)
                .displayName(displayName)
                .conversationState("REGISTERED")
                .isActive(true)
                .isBlocked(false)
                .subscribedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        em.persist(s);
        return s;
    }

    @Test
    @DisplayName("the same Telegram account can subscribe to two restaurants independently")
    void sameTelegramUserCanExistUnderTwoRestaurants() {
        subscriber(TENANT, 555_000L, "Seen by restaurant one");
        subscriber(OTHER,  555_000L, "Seen by restaurant two");
        em.flush();
        em.clear();

        var mine   = repo.findByTelegramUserIdAndRestaurantId(555_000L, TENANT);
        var theirs = repo.findByTelegramUserIdAndRestaurantId(555_000L, OTHER);

        assertTrue(mine.isPresent());
        assertTrue(theirs.isPresent());
        assertEquals("Seen by restaurant one", mine.get().getDisplayName());
        assertEquals("Seen by restaurant two", theirs.get().getDisplayName());
        assertNotEquals(mine.get().getId(), theirs.get().getId());

        // A restaurant this person never messaged sees nothing.
        assertTrue(repo.findByTelegramUserIdAndRestaurantId(555_000L, 999L).isEmpty());
    }

    @Test
    @DisplayName("broadcast targeting never crosses tenants")
    void targetableSubscribersAreScopedToOneRestaurant() {
        subscriber(TENANT, 1001L, "Mine A");
        subscriber(TENANT, 1002L, "Mine B");
        subscriber(OTHER,  2001L, "Theirs");
        em.flush();
        em.clear();

        var mine = repo.findTargetableSubscribers(TENANT);
        assertEquals(2, mine.size());
        assertTrue(mine.stream().allMatch(s -> TENANT.equals(s.getRestaurantId())));

        assertEquals(1, repo.findTargetableSubscribers(OTHER).size());
    }
}
