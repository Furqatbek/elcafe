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
        restaurant.setName("Test Restaurant");
        restaurant.setActive(true);
        restaurant.setAcceptingOrders(true);
        restaurant.setPhone("+998901111111");
        restaurant.setEmail("r1@test.com");
        restaurant.setCity("Tashkent");
        restaurant.setAddress("Address 1");
        restaurant.setDeliveryFee(BigDecimal.ZERO);
        em.persist(restaurant);

        // sub1: active, both alerts on, lastAlertSentAt = 2 hours ago
        em.persist(StockAlertSubscription.builder()
                .restaurant(restaurant)
                .telegramChatId(1001L)
                .subscriberName("Stock Manager A")
                .active(true)
                .alertOnLowStock(true)
                .alertOnReorder(true)
                .lastAlertSentAt(LocalDateTime.now().minusHours(2))
                .build());

        // sub2: active, lowStock=true, reorder=false, lastAlertSentAt = null
        em.persist(StockAlertSubscription.builder()
                .restaurant(restaurant)
                .telegramChatId(1002L)
                .subscriberName("Stock Manager B")
                .active(true)
                .alertOnLowStock(true)
                .alertOnReorder(false)
                .lastAlertSentAt(null)
                .build());

        // sub3: active, lowStock=false, reorder=true
        em.persist(StockAlertSubscription.builder()
                .restaurant(restaurant)
                .telegramChatId(1003L)
                .subscriberName("Stock Manager C")
                .active(true)
                .alertOnLowStock(false)
                .alertOnReorder(true)
                .lastAlertSentAt(LocalDateTime.now().minusMinutes(10))
                .build());

        // sub4: inactive
        em.persist(StockAlertSubscription.builder()
                .restaurant(restaurant)
                .telegramChatId(1004L)
                .subscriberName("Stock Manager D")
                .active(false)
                .alertOnLowStock(true)
                .alertOnReorder(true)
                .lastAlertSentAt(null)
                .build());

        em.flush();
        em.clear();
    }

    @Test @DisplayName("findActiveLowStockSubscriptions — returns active with alertOnLowStock=true")
    void findActiveLowStockSubscriptions() {
        List<StockAlertSubscription> result = repo.findActiveLowStockSubscriptions();

        // sub1 (active, lowStock=true), sub2 (active, lowStock=true)
        // sub3 (lowStock=false) and sub4 (inactive) excluded
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(s -> s.getActive() && s.getAlertOnLowStock()));
    }

    @Test @DisplayName("findActiveReorderSubscriptions — returns active with alertOnReorder=true")
    void findActiveReorderSubscriptions() {
        List<StockAlertSubscription> result = repo.findActiveReorderSubscriptions();

        // sub1 (active, reorder=true), sub3 (active, reorder=true)
        // sub2 (reorder=false) and sub4 (inactive) excluded
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(s -> s.getActive() && s.getAlertOnReorder()));
    }

    @Test @DisplayName("findEligibleForAlert — filters by restaurant, active, and cooldown")
    void findEligibleForAlert() {
        // Cooldown = 1 hour ago. sub1 sent 2 hours ago (eligible), sub2 never sent (eligible),
        // sub3 sent 10 min ago (NOT eligible — after cooldown), sub4 inactive (excluded)
        LocalDateTime cooldownTime = LocalDateTime.now().minusHours(1);
        List<StockAlertSubscription> result = repo.findEligibleForAlert(restaurant.getId(), cooldownTime);

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(s -> s.getTelegramChatId().equals(1001L)));
        assertTrue(result.stream().anyMatch(s -> s.getTelegramChatId().equals(1002L)));
    }

    @Test @DisplayName("findEligibleForAlert — with very recent cooldown returns all active")
    void findEligibleForAlert_noCooldown() {
        // Cooldown = now (all past alerts are before cooldown, so all active are eligible)
        LocalDateTime cooldownTime = LocalDateTime.now().plusMinutes(1);
        List<StockAlertSubscription> result = repo.findEligibleForAlert(restaurant.getId(), cooldownTime);

        // sub1, sub2, sub3 are all active and eligible
        assertEquals(3, result.size());
    }

    @Test @DisplayName("findEligibleForAlert — wrong restaurant returns empty")
    void findEligibleForAlert_wrongRestaurant() {
        List<StockAlertSubscription> result = repo.findEligibleForAlert(999L, LocalDateTime.now());
        assertTrue(result.isEmpty());
    }
}
