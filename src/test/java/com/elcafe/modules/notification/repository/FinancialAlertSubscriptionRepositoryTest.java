package com.elcafe.modules.notification.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.notification.entity.FinancialAlertSubscription;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class FinancialAlertSubscriptionRepositoryTest {

    @Autowired private FinancialAlertSubscriptionRepository repo;
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

        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDate today = LocalDate.now();

        // Subscription 1: eligible (lastReportDate = yesterday)
        em.persist(FinancialAlertSubscription.builder()
                .restaurant(restaurant).telegramChatId(111L)
                .subscriberName("Alice").active(true)
                .reportTime(LocalTime.of(22, 0))
                .lastReportDate(yesterday).build());

        // Subscription 2: NOT eligible (lastReportDate = today, already reported)
        em.persist(FinancialAlertSubscription.builder()
                .restaurant(restaurant).telegramChatId(222L)
                .subscriberName("Bob").active(true)
                .reportTime(LocalTime.of(23, 0))
                .lastReportDate(today).build());

        em.flush();
        em.clear();
    }

    @Test @DisplayName("findEligibleForDailyReport — returns subscriptions not yet reported today")
    void findEligibleForDailyReport() {
        List<FinancialAlertSubscription> result = repo.findEligibleForDailyReport(LocalDate.now());

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).getSubscriberName());
    }

    @Test @DisplayName("findEligibleForDailyReport — includes subscriptions with null lastReportDate")
    void findEligibleForDailyReport_nullDate() {
        em.persist(FinancialAlertSubscription.builder()
                .restaurant(restaurant).telegramChatId(333L)
                .subscriberName("Charlie").active(true)
                .lastReportDate(null).build());
        em.flush();
        em.clear();

        List<FinancialAlertSubscription> result = repo.findEligibleForDailyReport(LocalDate.now());

        assertEquals(2, result.size());
    }

    @Test @DisplayName("findEligibleForDailyReportByRestaurant — filters by restaurant")
    void findEligibleForDailyReportByRestaurant() {
        Restaurant other = new Restaurant();
        other.setName("Other");
        other.setActive(true);
        other.setAcceptingOrders(true);
        other.setPhone("+999");
        other.setEmail("o@o.com");
        other.setCity("O");
        other.setAddress("B");
        other.setDeliveryFee(BigDecimal.ZERO);
        em.persist(other);
        em.persist(FinancialAlertSubscription.builder()
                .restaurant(other).telegramChatId(444L)
                .subscriberName("Diana").active(true)
                .lastReportDate(null).build());
        em.flush();
        em.clear();

        List<FinancialAlertSubscription> result =
                repo.findEligibleForDailyReportByRestaurant(restaurant.getId(), LocalDate.now());

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).getSubscriberName());
    }

    @Test @DisplayName("findReadyToSend — filters by reportTime and lastReportDate")
    void findReadyToSend() {
        // At 22:30, Alice (reportTime=22:00) is ready, Bob already reported today
        List<FinancialAlertSubscription> result =
                repo.findReadyToSend(LocalTime.of(22, 30), LocalDate.now());

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).getSubscriberName());
    }

    @Test @DisplayName("findReadyToSend — returns empty when time is too early")
    void findReadyToSend_tooEarly() {
        List<FinancialAlertSubscription> result =
                repo.findReadyToSend(LocalTime.of(6, 0), LocalDate.now());

        assertTrue(result.isEmpty());
    }
}
