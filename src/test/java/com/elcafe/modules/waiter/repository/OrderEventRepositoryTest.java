package com.elcafe.modules.waiter.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.waiter.entity.OrderEvent;
import com.elcafe.modules.waiter.enums.OrderEventType;
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
class OrderEventRepositoryTest {

    @Autowired private OrderEventRepository orderEventRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private Order order;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);

        order = Order.builder()
                .restaurant(restaurant)
                .orderNumber("ORD-" + System.nanoTime())
                .status(OrderStatus.NEW)
                .subtotal(BigDecimal.TEN)
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .deliveryFee(BigDecimal.ZERO)
                .total(BigDecimal.TEN)
                .build();
        em.persist(order);
    }

    private OrderEvent createEvent(Order targetOrder, OrderEventType type, String triggeredBy) {
        OrderEvent event = OrderEvent.builder()
                .order(targetOrder)
                .eventType(type)
                .triggeredBy(triggeredBy)
                .build();
        em.persist(event);
        return event;
    }

    @Test
    @DisplayName("findByWaiterAndDateRange returns events for specific waiter within date range")
    void findByWaiterAndDateRange() {
        createEvent(order, OrderEventType.ORDER_CREATED, "Alice");
        createEvent(order, OrderEventType.ITEM_ADDED, "Alice");
        createEvent(order, OrderEventType.ORDER_CREATED, "Bob");

        em.flush();
        em.clear();

        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);

        List<OrderEvent> aliceEvents = orderEventRepository.findByWaiterAndDateRange(
                "Alice", start, end);

        assertEquals(2, aliceEvents.size());
        assertTrue(aliceEvents.stream().allMatch(e -> "Alice".equals(e.getTriggeredBy())));
    }

    @Test
    @DisplayName("countByEventTypeAndDateRange counts events of specific type within date range")
    void countByEventTypeAndDateRange() {
        createEvent(order, OrderEventType.ORDER_CREATED, "Alice");
        createEvent(order, OrderEventType.ORDER_CREATED, "Bob");
        createEvent(order, OrderEventType.PAYMENT_COMPLETED, "Alice");

        em.flush();
        em.clear();

        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now().plusDays(1);

        long createdCount = orderEventRepository.countByEventTypeAndDateRange(
                OrderEventType.ORDER_CREATED, start, end);
        long paymentCount = orderEventRepository.countByEventTypeAndDateRange(
                OrderEventType.PAYMENT_COMPLETED, start, end);
        long readyCount = orderEventRepository.countByEventTypeAndDateRange(
                OrderEventType.ORDER_READY, start, end);

        assertEquals(2L, createdCount);
        assertEquals(1L, paymentCount);
        assertEquals(0L, readyCount);
    }
}
