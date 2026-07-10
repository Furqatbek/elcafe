package com.elcafe.modules.order.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.enums.WaiterRole;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link OrderRepository#findByIdForNotification(Long)}: the @Async notification paths
 * (admin-panel WebSocket broadcast, owner-bot Telegram) read an order AFTER its loading session is
 * gone, so items, waiter, diningTable, customer, and restaurant must all be initialised at load
 * time. This finder exists because the {@code @EntityGraph} that used to sit on a redeclared
 * {@code findById} was silently ignored (Spring Data executed the override as a plain derived query
 * — this test originally asserted the graph and caught it) — with open-in-view off, the
 * uninitialised lazies then threw on the async thread and the admin broadcast was silently dropped
 * for every order.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class OrderFindByIdGraphTest {

    @Autowired private OrderRepository orderRepository;
    @Autowired private TestEntityManager em;

    @Test
    @DisplayName("findByIdForNotification initialises everything the async notification paths read")
    void notificationLoaderInitialisesAsyncReadAssociations() {
        Restaurant restaurant = em.persist(Restaurant.builder()
                .name("Graph Cafe").address("1 Graph St").active(true).build());

        Customer customer = new Customer();
        customer.setRestaurantId(restaurant.getId());
        customer.setFirstName("Graph");
        customer.setLastName("Customer");
        customer.setPhone("+998900000001");
        customer.setQrCode("graph-test-qr-0001");
        customer = em.persist(customer);

        Waiter waiter = new Waiter();
        waiter.setRestaurantId(restaurant.getId());
        waiter.setName("Graph Waiter");
        waiter.setPinCode("4321");
        waiter.setRole(WaiterRole.WAITER);
        waiter.setActive(true);
        waiter = em.persist(waiter);

        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(restaurant);
        table.setTableNumber("G1");
        table.setTableName("Graph Table");
        table.setStatus(RestaurantTable.TableStatus.AVAILABLE);
        table.setCapacity(2);
        table.setActive(true);
        table = em.persist(table);

        Order order = Order.builder()
                .orderNumber("GRAPH-1")
                .restaurant(restaurant)
                .customer(customer)
                .waiter(waiter)
                .diningTable(table)
                .subtotal(new BigDecimal("10"))
                .deliveryFee(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .total(new BigDecimal("10"))
                .items(new ArrayList<>())
                .build();
        order.addItem(OrderItem.builder()
                .productId(1L).productName("Graph Pizza").quantity(1)
                .unitPrice(new BigDecimal("10")).totalPrice(new BigDecimal("10"))
                .build());
        Long orderId = em.persistAndFlush(order).getId();
        em.clear();

        Order loaded = orderRepository.findByIdForNotification(orderId).orElseThrow();
        // Detach: initialised state must have been fetched at load time, not lazily on first touch.
        em.clear();

        assertThat(Hibernate.isInitialized(loaded.getItems())).as("items").isTrue();
        assertThat(Hibernate.isInitialized(loaded.getWaiter())).as("waiter").isTrue();
        assertThat(Hibernate.isInitialized(loaded.getDiningTable())).as("diningTable").isTrue();
        assertThat(Hibernate.isInitialized(loaded.getCustomer())).as("customer").isTrue();
        assertThat(Hibernate.isInitialized(loaded.getRestaurant())).as("restaurant").isTrue();

        // The exact detached reads broadcastToAdminPanel performs.
        assertThat(loaded.getItems()).hasSize(1);
        assertThat(loaded.getDiningTable().getTableNumber()).isEqualTo("G1");
        assertThat(loaded.getCustomer().getFirstName()).isEqualTo("Graph");
        assertThat(loaded.getRestaurant().getId()).isEqualTo(restaurant.getId());
    }
}
