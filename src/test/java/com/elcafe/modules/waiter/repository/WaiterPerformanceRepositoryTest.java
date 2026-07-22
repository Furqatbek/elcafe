package com.elcafe.modules.waiter.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.entity.WaiterPerformance;
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

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class WaiterPerformanceRepositoryTest {

    @Autowired private WaiterPerformanceRepository waiterPerformanceRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private Waiter waiter;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);

        waiter = Waiter.builder()
                .name("Jane Waiter")
                .pinCode("5678")
                .active(true)
                .build();
        em.persist(waiter);
    }

    private WaiterPerformance createPerformance(LocalDate date, int orders, BigDecimal revenue, BigDecimal bonus) {
        WaiterPerformance wp = WaiterPerformance.builder()
                .waiter(waiter)
                .restaurant(restaurant)
                .performanceDate(date)
                .totalOrders(orders)
                .totalRevenue(revenue)
                .totalTips(BigDecimal.ZERO)
                .bonusEarned(bonus)
                .kpiScore(new BigDecimal("85.00"))
                .build();
        em.persist(wp);
        return wp;
    }

    @Test
    @DisplayName("countWorkingDays counts distinct performance dates in range")
    void countWorkingDays() {
        LocalDate today = LocalDate.now();
        createPerformance(today, 10, new BigDecimal("500.00"), BigDecimal.ZERO);
        createPerformance(today.minusDays(1), 8, new BigDecimal("400.00"), BigDecimal.ZERO);
        createPerformance(today.minusDays(5), 5, new BigDecimal("200.00"), BigDecimal.ZERO);

        em.flush();
        em.clear();

        Long days = waiterPerformanceRepository.countWorkingDays(
                waiter.getId(), today.minusDays(3), today);

        assertEquals(2L, days);
    }

    @Test
    @DisplayName("getTotalBonusEarned sums bonus within date range")
    void getTotalBonusEarned() {
        LocalDate today = LocalDate.now();
        createPerformance(today, 10, new BigDecimal("500.00"), new BigDecimal("25.00"));
        createPerformance(today.minusDays(1), 8, new BigDecimal("400.00"), new BigDecimal("15.00"));
        // Outside range
        createPerformance(today.minusDays(10), 5, new BigDecimal("200.00"), new BigDecimal("100.00"));

        em.flush();
        em.clear();

        BigDecimal totalBonus = waiterPerformanceRepository.getTotalBonusEarned(
                waiter.getId(), today.minusDays(3), today);

        assertEquals(0, new BigDecimal("40.00").compareTo(totalBonus));
    }
}
