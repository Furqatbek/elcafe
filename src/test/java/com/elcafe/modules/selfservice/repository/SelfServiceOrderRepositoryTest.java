package com.elcafe.modules.selfservice.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.selfservice.entity.SelfServiceOrder;
import com.elcafe.modules.selfservice.entity.SelfServiceSession;
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

    @Test @DisplayName("findPendingByRestaurant — excludes terminal statuses")
    void findPendingByRestaurant_excludesTerminalStatuses() {
        Order oPending = persistOrder("ORD-020", OrderStatus.PENDING);
        Order oCompleted = persistOrder("ORD-021", OrderStatus.COMPLETED);
        Order oCancelled = persistOrder("ORD-022", OrderStatus.CANCELLED);

        em.persist(SelfServiceOrder.builder().order(oPending).orderType(SelfServiceOrderType.DINE_IN).build());
        em.persist(SelfServiceOrder.builder().order(oCompleted).orderType(SelfServiceOrderType.DINE_IN)
                .actualReadyTime(LocalDateTime.now().minusMinutes(30)).build());
        em.persist(SelfServiceOrder.builder().order(oCancelled).orderType(SelfServiceOrderType.TAKEAWAY).build());

        em.flush(); em.clear();

        List<SelfServiceOrder> pending = selfServiceOrderRepository.findPendingByRestaurant(restaurant.getId());

        assertEquals(1, pending.size());
        assertEquals("ORD-020", pending.get(0).getOrder().getOrderNumber());
    }

    @Test @DisplayName("countByOrderType — groups correctly")
    void countByOrderType_groupsCorrectly() {
        Order o1 = persistOrder("ORD-030", OrderStatus.PENDING);
        Order o2 = persistOrder("ORD-031", OrderStatus.PENDING);
        Order o3 = persistOrder("ORD-032", OrderStatus.PENDING);

        em.persist(SelfServiceOrder.builder().order(o1).orderType(SelfServiceOrderType.DINE_IN).build());
        em.persist(SelfServiceOrder.builder().order(o2).orderType(SelfServiceOrderType.DINE_IN).build());
        em.persist(SelfServiceOrder.builder().order(o3).orderType(SelfServiceOrderType.TAKEAWAY).build());

        em.flush(); em.clear();

        List<Object[]> results = selfServiceOrderRepository.countByOrderType(restaurant.getId());

        assertEquals(2, results.size());
        long totalCount = results.stream().mapToLong(r -> (Long) r[1]).sum();
        assertEquals(3L, totalCount);
    }

    @Test @DisplayName("findBySessionId — returns all for session")
    void findBySessionId_returnsAllForSession() {
        RestaurantTable table = RestaurantTable.builder()
                .restaurant(restaurant)
                .tableNumber("T1")
                .tableName("Table One")
                .status(RestaurantTable.TableStatus.AVAILABLE)
                .capacity(4)
                .active(true)
                .build();
        em.persist(table);

        SelfServiceSession session1 = SelfServiceSession.builder()
                .sessionToken("session-1")
                .restaurant(restaurant)
                .table(table)
                .isActive(true)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        em.persist(session1);

        SelfServiceSession session2 = SelfServiceSession.builder()
                .sessionToken("session-2")
                .restaurant(restaurant)
                .table(table)
                .isActive(true)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
        em.persist(session2);

        Order o1 = persistOrder("ORD-040", OrderStatus.PENDING);
        Order o2 = persistOrder("ORD-041", OrderStatus.PENDING);
        Order o3 = persistOrder("ORD-042", OrderStatus.PENDING);

        em.persist(SelfServiceOrder.builder().order(o1).session(session1).orderType(SelfServiceOrderType.DINE_IN).build());
        em.persist(SelfServiceOrder.builder().order(o2).session(session1).orderType(SelfServiceOrderType.TAKEAWAY).build());
        em.persist(SelfServiceOrder.builder().order(o3).session(session2).orderType(SelfServiceOrderType.DINE_IN).build());

        em.flush(); em.clear();

        List<SelfServiceOrder> orders = selfServiceOrderRepository.findBySessionId(session1.getId());

        assertEquals(2, orders.size());
    }
}
