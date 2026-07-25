package com.elcafe.modules.telegram.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.telegram.entity.TelegramSubscriber;
import com.elcafe.modules.telegram.entity.TelegramSubscriberLocation;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class TelegramSubscriberLocationRepositoryTest {

    @Autowired private TelegramSubscriberLocationRepository repo;
    @Autowired private EntityManager em;

    private TelegramSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = TelegramSubscriber.builder()
                .restaurantId(1L)
                .telegramUserId(5001L)
                .username("loc_user")
                .firstName("Location")
                .lastName("Tester")
                .isActive(true)
                .isBlocked(false)
                .build();
        em.persist(subscriber);

        em.persist(TelegramSubscriberLocation.builder()
                .restaurantId(1L)   // V164: mirrors the parent subscriber's tenant
                .subscriber(subscriber)
                .latitude(41.311081)
                .longitude(69.240562)
                .label("Home")
                .isDefault(true)
                .build());

        em.persist(TelegramSubscriberLocation.builder()
                .restaurantId(1L)   // V164: mirrors the parent subscriber's tenant
                .subscriber(subscriber)
                .latitude(41.299496)
                .longitude(69.240074)
                .label("Work")
                .isDefault(false)
                .build());

        em.flush();
        em.clear();

        // re-read so the managed reference is available for queries
        subscriber = em.find(TelegramSubscriber.class, subscriber.getId());
    }

    @Test @DisplayName("findAllBySubscriber — returns all locations for a subscriber")
    void findAllBySubscriber() {
        List<TelegramSubscriberLocation> locations = repo.findAllBySubscriber(subscriber);

        assertEquals(2, locations.size());
        assertTrue(locations.stream().anyMatch(l -> "Home".equals(l.getLabel())));
        assertTrue(locations.stream().anyMatch(l -> "Work".equals(l.getLabel())));
    }

    @Test @DisplayName("countBySubscriber — correct count")
    void countBySubscriber() {
        long count = repo.countBySubscriber(subscriber);

        assertEquals(2, count);
    }

    @Test @DisplayName("findAllBySubscriber — returns empty for subscriber with no locations")
    void findAllBySubscriber_empty() {
        TelegramSubscriber other = TelegramSubscriber.builder()
                .restaurantId(1L)
                .telegramUserId(5002L)
                .username("no_loc_user")
                .firstName("No")
                .lastName("Locations")
                .isActive(true)
                .isBlocked(false)
                .build();
        em.persist(other);
        em.flush();
        em.clear();

        other = em.find(TelegramSubscriber.class, other.getId());
        List<TelegramSubscriberLocation> locations = repo.findAllBySubscriber(other);

        assertTrue(locations.isEmpty());
        assertEquals(0, repo.countBySubscriber(other));
    }
}
