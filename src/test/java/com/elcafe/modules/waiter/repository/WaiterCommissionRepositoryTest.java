package com.elcafe.modules.waiter.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.entity.WaiterCommission;
import com.elcafe.modules.waiter.enums.CommissionStatus;
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

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class WaiterCommissionRepositoryTest {

    @Autowired private WaiterCommissionRepository waiterCommissionRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private Waiter waiter;
    private int orderSeq = 0;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);

        waiter = Waiter.builder()
                .restaurantId(restaurant.getId())
                .name("John Waiter")
                .pinCode("1234")
                .active(true)
                .build();
        em.persist(waiter);
    }

    private WaiterCommission createCommission(CommissionStatus status, BigDecimal amount) {
        Order order = Order.builder()
                .restaurant(restaurant)
                .orderNumber("ORD-" + System.nanoTime() + "-" + (++orderSeq))
                .status(OrderStatus.COMPLETED)
                .subtotal(new BigDecimal("100.00"))
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .deliveryFee(BigDecimal.ZERO)
                .total(new BigDecimal("100.00"))
                .build();
        em.persist(order);

        WaiterCommission wc = WaiterCommission.builder()
                .waiter(waiter)
                .order(order)
                .restaurant(restaurant)
                .orderTotal(new BigDecimal("100.00"))
                .commissionPercent(new BigDecimal("5.00"))
                .commissionAmount(amount)
                .status(status)
                .build();
        em.persist(wc);
        return wc;
    }

    @Test
    @DisplayName("getTotalCommissionByWaiterAndDateRange sums commissions within date range")
    void getTotalCommissionByWaiterAndDateRange() {
        createCommission(CommissionStatus.PENDING, new BigDecimal("5.00"));
        createCommission(CommissionStatus.PAID, new BigDecimal("10.00"));

        em.flush();
        em.clear();

        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);

        BigDecimal total = waiterCommissionRepository.getTotalCommissionByWaiterAndDateRange(
                waiter.getId(), start, end);

        assertEquals(0, new BigDecimal("15.00").compareTo(total));
    }

    @Test
    @DisplayName("getTotalPendingCommissionByWaiter sums only PENDING commissions")
    void getTotalPendingCommissionByWaiter() {
        createCommission(CommissionStatus.PENDING, new BigDecimal("5.00"));
        createCommission(CommissionStatus.PENDING, new BigDecimal("7.50"));
        createCommission(CommissionStatus.PAID, new BigDecimal("20.00"));

        em.flush();
        em.clear();

        BigDecimal pending = waiterCommissionRepository.getTotalPendingCommissionByWaiter(waiter.getId());

        assertEquals(0, new BigDecimal("12.50").compareTo(pending));
    }

    @Test
    @DisplayName("findByWaiterIdAndDateRange returns commissions in date range ordered by createdAt DESC")
    void findByWaiterIdAndDateRange() {
        createCommission(CommissionStatus.PENDING, new BigDecimal("5.00"));
        createCommission(CommissionStatus.PAID, new BigDecimal("10.00"));

        em.flush();
        em.clear();

        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);

        List<WaiterCommission> results = waiterCommissionRepository.findByWaiterIdAndDateRange(
                waiter.getId(), start, end);

        assertEquals(2, results.size());
        // Verify DESC ordering: second created should come first
        assertTrue(results.get(0).getCreatedAt().compareTo(results.get(1).getCreatedAt()) >= 0);
    }
}
