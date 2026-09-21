package com.elcafe.modules.kitchen.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.kitchen.entity.KitchenOrder;
import com.elcafe.modules.kitchen.enums.KitchenOrderStatus;
import com.elcafe.modules.kitchen.enums.KitchenPriority;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class KitchenOrderRepositoryTest {

    @Autowired private KitchenOrderRepository kitchenOrderRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);
    }

    private Order createOrder() {
        Order order = Order.builder()
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
        return order;
    }

    private KitchenOrder createKitchenOrder(KitchenOrderStatus status, KitchenPriority priority) {
        KitchenOrder ko = KitchenOrder.builder()
                .order(createOrder())
                .status(status)
                .priority(priority)
                .build();
        em.persist(ko);
        return ko;
    }

    @Test
    @DisplayName("countByStatus returns correct count for each status")
    void countByStatus() {
        createKitchenOrder(KitchenOrderStatus.PENDING, KitchenPriority.NORMAL);
        createKitchenOrder(KitchenOrderStatus.PENDING, KitchenPriority.HIGH);
        createKitchenOrder(KitchenOrderStatus.PREPARING, KitchenPriority.NORMAL);

        em.flush();
        em.clear();

        Long pendingCount = kitchenOrderRepository.countByStatus(KitchenOrderStatus.PENDING);
        Long preparingCount = kitchenOrderRepository.countByStatus(KitchenOrderStatus.PREPARING);
        Long readyCount = kitchenOrderRepository.countByStatus(KitchenOrderStatus.READY);

        assertEquals(2L, pendingCount);
        assertEquals(1L, preparingCount);
        assertEquals(0L, readyCount);
    }

    @Test
    @DisplayName("findByRestaurantAndStatuses returns orders filtered by restaurant and statuses")
    void findByRestaurantAndStatuses() {
        Restaurant otherRestaurant = new Restaurant();
        otherRestaurant.setName("Other Restaurant");
        otherRestaurant.setAddress("456 Other St");
        otherRestaurant.setActive(true);
        em.persist(otherRestaurant);

        // Orders for our restaurant
        createKitchenOrder(KitchenOrderStatus.PENDING, KitchenPriority.HIGH);
        createKitchenOrder(KitchenOrderStatus.PREPARING, KitchenPriority.NORMAL);
        createKitchenOrder(KitchenOrderStatus.READY, KitchenPriority.NORMAL);

        // Order for other restaurant
        Order otherOrder = Order.builder()
                .restaurant(otherRestaurant)
                .orderNumber("ORD-OTHER-" + System.nanoTime())
                .status(OrderStatus.NEW)
                .subtotal(BigDecimal.TEN)
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .deliveryFee(BigDecimal.ZERO)
                .total(BigDecimal.TEN)
                .build();
        em.persist(otherOrder);
        KitchenOrder otherKo = KitchenOrder.builder()
                .order(otherOrder)
                .status(KitchenOrderStatus.PENDING)
                .priority(KitchenPriority.NORMAL)
                .build();
        em.persist(otherKo);

        em.flush();
        em.clear();

        List<KitchenOrder> results = kitchenOrderRepository.findByRestaurantAndStatuses(
                restaurant.getId(),
                List.of(KitchenOrderStatus.PENDING, KitchenOrderStatus.PREPARING));

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(ko ->
                ko.getStatus() == KitchenOrderStatus.PENDING || ko.getStatus() == KitchenOrderStatus.PREPARING));
    }

    @Test
    @DisplayName("getAveragePreparationTime returns average for completed orders with actual times")
    void getAveragePreparationTime() {
        KitchenOrder ko1 = createKitchenOrder(KitchenOrderStatus.PICKED_UP, KitchenPriority.NORMAL);
        ko1.setActualPreparationTimeMinutes(20);

        KitchenOrder ko2 = createKitchenOrder(KitchenOrderStatus.READY, KitchenPriority.NORMAL);
        ko2.setActualPreparationTimeMinutes(30);

        // Order without actual time should be excluded
        createKitchenOrder(KitchenOrderStatus.READY, KitchenPriority.NORMAL);

        // Pending order should be excluded
        KitchenOrder ko4 = createKitchenOrder(KitchenOrderStatus.PENDING, KitchenPriority.NORMAL);
        ko4.setActualPreparationTimeMinutes(100);

        em.flush();
        em.clear();

        Double avg = kitchenOrderRepository.getAveragePreparationTime(
                List.of(KitchenOrderStatus.READY, KitchenOrderStatus.PICKED_UP));

        assertNotNull(avg);
        assertEquals(25.0, avg, 0.01);
    }
}
