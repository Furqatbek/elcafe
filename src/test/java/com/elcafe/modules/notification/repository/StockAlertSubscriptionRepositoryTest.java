package com.elcafe.modules.notification.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.notification.entity.StockAlertSubscription;
import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class StockAlertSubscriptionRepositoryTest {

    @Autowired private StockAlertSubscriptionRepository repo;
    @Autowired private EntityManager em;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test");
        restaurant.setActive(true);
        restaurant.setAcceptingOrders(true);
        restaurant.setPhone("+998");
        restaurant.setEmail("t@t.com");
        restaurant.setCity("T");
        restaurant.setAddress("A");
        restaurant.setDeliveryFee(BigDecimal.ZERO);
        em.persist(restaurant);

        // Sub 1: active, both alerts on, no previous alert
        em.persist(StockAlertSubscription.builder()
                .restaurant(restaurant).telegramChatId(111L)
                .subscriberName("Alice").active(true)
                .alertOnLowStock(true).alertOnReorder(true)
                .lastAlertSentAt(null).build());

        // Sub 2: active, only lowStock on, recent alert
        em.persist(StockAlertSubscription.builder()
                .restaurant(restaurant).telegramChatId(222L)
                .subscriberName("Bob").active(true)
                .alertOnLowStock(true).alertOnReorder(false)
                .lastAlertSentAt(LocalDateTime.now().minusMinutes(5)).build());

        // Sub 3: inactive
        em.persist(StockAlertSubscription.builder()
                .restaurant(restaurant).telegramChatId(333L)
                .subscriberName("Charlie").active(false)
                .alertOnLowStock(true).alertOnReorder(true)
                .lastAlertSentAt(null).build());

        em.flush();
        em.clear();
    }

    @Test @DisplayName("findActiveLowStockSubscriptions — returns active subs with alertOnLowStock=true")
    void findActiveLowStockSubscriptions() {
        List<StockAlertSubscription> result = repo.findActiveLowStockSubscriptions();

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(s -> s.getActive() && s.getAlertOnLowStock()));
    }

    @Test @DisplayName("findActiveReorderSubscriptions — returns active subs with alertOnReorder=true")
    void findActiveReorderSubscriptions() {
        List<StockAlertSubscription> result = repo.findActiveReorderSubscriptions();

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).getSubscriberName());
    }

    @Test @DisplayName("findEligibleForAlert — returns subs with no recent alert")
    void findEligibleForAlert_noRecentAlert() {
        // Cooldown: 1 hour ago — Alice (null lastAlertSentAt) is eligible,
        // Bob (5 min ago) is NOT eligible because lastAlertSentAt > cooldownTime
        LocalDateTime cooldownTime = LocalDateTime.now().minusHours(1);
        List<StockAlertSubscription> result =
                repo.findEligibleForAlert(restaurant.getId(), cooldownTime);

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).getSubscriberName());
    }

    @Test @DisplayName("findEligibleForAlert — includes subs past cooldown period")
    void findEligibleForAlert_pastCooldown() {
        // Cooldown set to now — Bob's alert (5 min ago) is before now, so eligible
        LocalDateTime cooldownTime = LocalDateTime.now();
        List<StockAlertSubscription> result =
                repo.findEligibleForAlert(restaurant.getId(), cooldownTime);

        assertEquals(2, result.size());
    }

    @Test @DisplayName("findEligibleForAlert — excludes inactive subscriptions")
    void findEligibleForAlert_excludesInactive() {
        // Even with generous cooldown, Charlie (inactive) should not appear
        LocalDateTime cooldownTime = LocalDateTime.now().plusHours(1);
        List<StockAlertSubscription> result =
                repo.findEligibleForAlert(restaurant.getId(), cooldownTime);

        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(s -> "Charlie".equals(s.getSubscriberName())));
    }

    @Test @DisplayName("findEligibleForAlert — filters by restaurant")
    void findEligibleForAlert_filtersByRestaurant() {
        LocalDateTime cooldownTime = LocalDateTime.now().plusHours(1);
        List<StockAlertSubscription> result =
                repo.findEligibleForAlert(9999L, cooldownTime);

        assertTrue(result.isEmpty());
    }
}
