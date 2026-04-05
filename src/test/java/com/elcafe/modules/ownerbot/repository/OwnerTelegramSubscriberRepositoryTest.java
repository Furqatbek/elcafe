package com.elcafe.modules.ownerbot.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.ownerbot.entity.OwnerTelegramSubscriber;
import com.elcafe.modules.restaurant.entity.Restaurant;
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

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class OwnerTelegramSubscriberRepositoryTest {

    @Autowired private OwnerTelegramSubscriberRepository repo;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private OwnerTelegramSubscriber activeOwner;
    private OwnerTelegramSubscriber activeManager;
    private OwnerTelegramSubscriber unverifiedSub;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("R");
        restaurant.setAddress("A");
        restaurant.setActive(true);
        em.persist(restaurant);

        activeOwner = OwnerTelegramSubscriber.builder()
                .restaurant(restaurant)
                .telegramUserId(1001L)
                .username("owner_user")
                .firstName("Alice")
                .role("OWNER")
                .isActive(true)
                .isVerified(true)
                .build();
        em.persist(activeOwner);

        activeManager = OwnerTelegramSubscriber.builder()
                .restaurant(restaurant)
                .telegramUserId(1002L)
                .username("manager_user")
                .firstName("Bob")
                .role("MANAGER")
                .isActive(true)
                .isVerified(true)
                .build();
        em.persist(activeManager);

        unverifiedSub = OwnerTelegramSubscriber.builder()
                .restaurant(restaurant)
                .telegramUserId(1003L)
                .username("unverified_user")
                .firstName("Charlie")
                .role("OWNER")
                .isActive(true)
                .isVerified(false)
                .build();
        em.persist(unverifiedSub);

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("findActiveSubscribersByRestaurantAndRoles - filters by active, verified, and roles")
    void findActiveSubscribersByRestaurantAndRoles() {
        List<OwnerTelegramSubscriber> subscribers =
                repo.findActiveSubscribersByRestaurantAndRoles(
                        restaurant.getId(), List.of("OWNER", "MANAGER"));

        // activeOwner and activeManager are active+verified with matching roles
        // unverifiedSub is not verified, so excluded
        assertEquals(2, subscribers.size());
        assertTrue(subscribers.stream().noneMatch(s -> s.getTelegramUserId().equals(1003L)));
    }

    @Test
    @DisplayName("countActiveByRestaurantId - counts all active subscribers including unverified")
    void countActiveByRestaurantId() {
        long count = repo.countActiveByRestaurantId(restaurant.getId());

        // All 3 are active (isActive=true), the query only checks isActive
        assertEquals(3, count);
    }

    @Test
    @DisplayName("findActiveSubscribersWithSettings - returns active+verified with LEFT JOIN FETCH settings")
    void findActiveSubscribersWithSettings() {
        List<OwnerTelegramSubscriber> subscribers =
                repo.findActiveSubscribersWithSettings(restaurant.getId());

        // Only active+verified: activeOwner and activeManager
        assertEquals(2, subscribers.size());
        assertTrue(subscribers.stream().allMatch(s -> s.getIsActive() && s.getIsVerified()));
        // notificationSettings should be accessible (LEFT JOIN FETCH, null is fine)
        subscribers.forEach(s -> assertNull(s.getNotificationSettings()));
    }
}
