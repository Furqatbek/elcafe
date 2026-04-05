package com.elcafe.modules.selfservice.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.selfservice.entity.SelfServiceOrder;
import com.elcafe.modules.selfservice.enums.SelfServiceOrderType;
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
class SelfServiceOrderRepositoryTest {

    @Autowired private SelfServiceOrderRepository selfServiceOrderRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = Restaurant.builder().name("Test Cafe").address("123 Main St").build();
        em.persist(restaurant);
    }

    private Order persistOrder(String orderNumber, OrderStatus status) {
        Order o = Order.builder()
                .orderNumber(orderNumber).restaurant(restaurant).status(status)
                .subtotal(BigDecimal.TEN).deliveryFee(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO).discount(BigDecimal.ZERO).total(BigDecimal.TEN)
                .build();
        em.persist(o);
        return o;
    }

    @Test @DisplayName("findReadyForPickup — returns orders with actualReadyTime set but not picked up")
    void findReadyForPickup() {
        Order o1 = persistOrder("ORD-001", OrderStatus.READY);
        Order o2 = persistOrder("ORD-002", OrderStatus.READY);
        Order o3 = persistOrder("ORD-003", OrderStatus.PREPARING);

        em.persist(SelfServiceOrder.builder().order(o1).orderType(SelfServiceOrderType.DINE_IN)
                .actualReadyTime(LocalDateTime.now().minusMinutes(10)).build());
        em.persist(SelfServiceOrder.builder().order(o2).orderType(SelfServiceOrderType.TAKEAWAY)
                .actualReadyTime(LocalDateTime.now().minusMinutes(5))
                .pickedUpAt(LocalDateTime.now()).build());
        em.persist(SelfServiceOrder.builder().order(o3).orderType(SelfServiceOrderType.DINE_IN).build());

        em.flush(); em.clear();

        List<SelfServiceOrder> ready = selfServiceOrderRepository.findReadyForPickup(restaurant.getId());

        assertEquals(1, ready.size());
        assertEquals("ORD-001", ready.get(0).getOrder().getOrderNumber());
    }

    @Test @DisplayName("countByRestaurantAndDateRange — counts orders in date range")
    void countByRestaurantAndDateRange() {
        LocalDateTime now = LocalDateTime.now();

        Order o1 = persistOrder("ORD-010", OrderStatus.COMPLETED);
        Order o2 = persistOrder("ORD-011", OrderStatus.COMPLETED);
        Order o3 = persistOrder("ORD-012", OrderStatus.COMPLETED);

        em.persist(SelfServiceOrder.builder().order(o1).orderType(SelfServiceOrderType.DINE_IN)
                .createdAt(now.minusDays(2)).build());
        em.persist(SelfServiceOrder.builder().order(o2).orderType(SelfServiceOrderType.TAKEAWAY)
                .createdAt(now.minusDays(1)).build());
        em.persist(SelfServiceOrder.builder().order(o3).orderType(SelfServiceOrderType.DINE_IN)
                .createdAt(now.minusDays(10)).build());

        em.flush(); em.clear();

        long count = selfServiceOrderRepository.countByRestaurantAndDateRange(
                restaurant.getId(), now.minusDays(3), now);

        assertEquals(2L, count);
    }
}
