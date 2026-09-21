package com.elcafe.modules.order.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class OrderRepositoryTest {

    @Autowired private OrderRepository orderRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private Customer customer;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);

        customer = Customer.builder()
                .firstName("John").lastName("Doe").phone("1234567890")
                .active(true)
                .build();
        em.persist(customer);
    }

    private Order createOrder(OrderStatus status, BigDecimal total) {
        Order order = Order.builder()
                .restaurant(restaurant)
                .orderNumber("ORD-" + System.nanoTime())
                .status(status)
                .subtotal(total)
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .deliveryFee(BigDecimal.ZERO)
                .total(total)
                .build();
        em.persist(order);
        return order;
    }

    @Test
    @DisplayName("sumTotalByCustomerId returns sum of all order totals for a customer")
    void sumTotalByCustomerId() {
        Order o1 = createOrder(OrderStatus.COMPLETED, new BigDecimal("50.00"));
        o1.setCustomer(customer);
        Order o2 = createOrder(OrderStatus.COMPLETED, new BigDecimal("30.00"));
        o2.setCustomer(customer);
        // Order without customer should not count
        createOrder(OrderStatus.COMPLETED, new BigDecimal("100.00"));

        em.flush();
        em.clear();

        BigDecimal sum = orderRepository.sumTotalByCustomerId(customer.getId());
        assertEquals(0, new BigDecimal("80.00").compareTo(sum));
    }

    @Test
    @DisplayName("countActiveByRestaurantIdAndStatus counts non-deleted orders by status")
    void countActiveByRestaurantIdAndStatus() {
        createOrder(OrderStatus.NEW, BigDecimal.TEN);
        createOrder(OrderStatus.NEW, BigDecimal.TEN);
        Order deleted = createOrder(OrderStatus.NEW, BigDecimal.TEN);
        deleted.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        // Different status
        createOrder(OrderStatus.COMPLETED, BigDecimal.TEN);

        em.flush();
        em.clear();

        long count = orderRepository.countActiveByRestaurantIdAndStatus(restaurant.getId(), OrderStatus.NEW);
        assertEquals(2, count);
    }

    @Test
    @DisplayName("getDailyStatsForRestaurant returns count and revenue excluding cancelled/rejected")
    void getDailyStatsForRestaurant() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime startOfDay = now.toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime endOfDay = startOfDay.plusDays(1);

        createOrder(OrderStatus.COMPLETED, new BigDecimal("40.00"));
        createOrder(OrderStatus.COMPLETED, new BigDecimal("60.00"));
        // Cancelled order should be excluded
        createOrder(OrderStatus.CANCELLED, new BigDecimal("25.00"));
        // Rejected order should be excluded
        createOrder(OrderStatus.REJECTED, new BigDecimal("15.00"));

        em.flush();
        em.clear();

        Object[] raw = orderRepository.getDailyStatsForRestaurant(
                restaurant.getId(), startOfDay, endOfDay);
        // Spring Data may wrap single-row aggregate results — unwrap if needed
        Object[] stats = (raw[0] instanceof Object[]) ? (Object[]) raw[0] : raw;

        assertEquals(2L, stats[0]);
        assertEquals(0, new BigDecimal("100.00").compareTo((BigDecimal) stats[1]));
    }
}
