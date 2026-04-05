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
    private Restaurant restaurant2;

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

        restaurant2 = new Restaurant();
        restaurant2.setName("Test Restaurant 2");
        restaurant2.setActive(true);
        restaurant2.setAcceptingOrders(true);
        restaurant2.setPhone("+998902222222");
        restaurant2.setEmail("r2@test.com");
        restaurant2.setCity("Tashkent");
        restaurant2.setAddress("Address 2");
        restaurant2.setDeliveryFee(BigDecimal.ZERO);
        em.persist(restaurant2);

        // sub1: active, lastReportDate = yesterday, reportTime = 22:00
        em.persist(FinancialAlertSubscription.builder()
                .restaurant(restaurant)
                .telegramChatId(1001L)
                .subscriberName("Manager A")
                .active(true)
                .reportTime(LocalTime.of(22, 0))
                .lastReportDate(LocalDate.now().minusDays(1))
                .build());

        // sub2: active, lastReportDate = today (already sent), reportTime = 23:00
        em.persist(FinancialAlertSubscription.builder()
                .restaurant(restaurant)
                .telegramChatId(1002L)
                .subscriberName("Manager B")
                .active(true)
                .reportTime(LocalTime.of(23, 0))
                .lastReportDate(LocalDate.now())
                .build());

        // sub3: active, lastReportDate = null (never sent), restaurant2, reportTime = 20:00
        em.persist(FinancialAlertSubscription.builder()
                .restaurant(restaurant2)
                .telegramChatId(2001L)
                .subscriberName("Manager C")
                .active(true)
                .reportTime(LocalTime.of(20, 0))
                .lastReportDate(null)
                .build());

        // sub4: inactive
        em.persist(FinancialAlertSubscription.builder()
                .restaurant(restaurant2)
                .telegramChatId(2002L)
                .subscriberName("Manager D")
                .active(false)
                .reportTime(LocalTime.of(23, 0))
                .lastReportDate(null)
                .build());

        em.flush();
        em.clear();
    }

    @Test @DisplayName("findEligibleForDailyReport — returns active subs where report not sent today")
    void findEligibleForDailyReport() {
        LocalDate today = LocalDate.now();
        List<FinancialAlertSubscription> result = repo.findEligibleForDailyReport(today);

        // sub1 (lastReportDate=yesterday), sub3 (lastReportDate=null) are eligible
        // sub2 (lastReportDate=today) and sub4 (inactive) are excluded
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(s -> s.getTelegramChatId().equals(1001L)));
        assertTrue(result.stream().anyMatch(s -> s.getTelegramChatId().equals(2001L)));
    }

    @Test @DisplayName("findEligibleForDailyReportByRestaurant — filters by restaurant")
    void findEligibleForDailyReportByRestaurant() {
        LocalDate today = LocalDate.now();

        List<FinancialAlertSubscription> r1Result = repo.findEligibleForDailyReportByRestaurant(
                restaurant.getId(), today);
        assertEquals(1, r1Result.size());
        assertEquals(1001L, r1Result.get(0).getTelegramChatId());

        List<FinancialAlertSubscription> r2Result = repo.findEligibleForDailyReportByRestaurant(
                restaurant2.getId(), today);
        assertEquals(1, r2Result.size());
        assertEquals(2001L, r2Result.get(0).getTelegramChatId());
    }

    @Test @DisplayName("findReadyToSend — filters by reportTime and lastReportDate")
    void findReadyToSend() {
        LocalDate today = LocalDate.now();

        // At 21:00: sub1 (reportTime=22:00) is NOT ready, sub3 (reportTime=20:00) IS ready
        List<FinancialAlertSubscription> at21 = repo.findReadyToSend(LocalTime.of(21, 0), today);
        assertEquals(1, at21.size());
        assertEquals(2001L, at21.get(0).getTelegramChatId());

        // At 23:00: sub1 (reportTime=22:00) and sub3 (reportTime=20:00) are ready
        List<FinancialAlertSubscription> at23 = repo.findReadyToSend(LocalTime.of(23, 0), today);
        assertEquals(2, at23.size());
    }

    @Test @DisplayName("findReadyToSend — excludes already-sent-today subscriptions")
    void findReadyToSend_excludesSentToday() {
        LocalDate today = LocalDate.now();
        // sub2 has lastReportDate=today so it should never appear even at 23:59
        List<FinancialAlertSubscription> result = repo.findReadyToSend(LocalTime.of(23, 59), today);
        assertTrue(result.stream().noneMatch(s -> s.getTelegramChatId().equals(1002L)));
    }
}
