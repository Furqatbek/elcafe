package com.elcafe.modules.order.integration;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.repository.PaymentRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.entity.RestaurantTable.TableStatus;
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
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class POSOrderIntegrationTest {

    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private RestaurantTable table;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("POS Test Restaurant");
        restaurant.setAddress("456 POS St");
        restaurant.setCity("Tashkent");
        restaurant.setPhone("+998900000000");
        restaurant.setEmail("pos@test.com");
        restaurant.setActive(true);
        restaurant.setAcceptingOrders(true);
        restaurant.setDeliveryFee(BigDecimal.ZERO);
        em.persist(restaurant);

        table = new RestaurantTable();
        table.setRestaurant(restaurant);
        table.setTableNumber("POS-1");
        table.setTableName("POS Table 1");
        table.setStatus(TableStatus.AVAILABLE);
        table.setCapacity(4);
        table.setActive(true);
        em.persist(table);

        em.flush();
    }

    @Test
    @DisplayName("Full POS flow: create → add items → payment → close")
    void fullPOSFlow_createToClose() {
        // 1. Create order
        Order order = Order.builder()
                .orderNumber("POS-FLOW-001")
                .restaurant(restaurant)
                .diningTable(table)
                .status(OrderStatus.NEW)
                .orderType(OrderType.DINE_IN)
                .orderSource(OrderSource.ADMIN_PANEL)
                .subtotal(BigDecimal.ZERO)
                .deliveryFee(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .grandTotal(BigDecimal.ZERO)
                .items(new ArrayList<>())
                .build();
        order = orderRepository.save(order);
        em.flush();
        em.clear();

        // 2. Add items
        Order loaded = orderRepository.findById(order.getId()).orElseThrow();
        loaded.addItem(OrderItem.builder()
                .productId(1L).productName("Steak").quantity(1)
                .unitPrice(BigDecimal.valueOf(80000)).totalPrice(BigDecimal.valueOf(80000)).build());
        loaded.addItem(OrderItem.builder()
                .productId(2L).productName("Salad").quantity(2)
                .unitPrice(BigDecimal.valueOf(15000)).totalPrice(BigDecimal.valueOf(30000)).build());
        loaded.setSubtotal(BigDecimal.valueOf(110000));
        loaded.setTotal(BigDecimal.valueOf(110000));
        loaded.setGrandTotal(BigDecimal.valueOf(110000));
        loaded.setStatus(OrderStatus.PREPARING);
        orderRepository.save(loaded);
        em.flush();
        em.clear();

        // Verify items
        loaded = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(2, loaded.getItems().size());
        assertEquals(OrderStatus.PREPARING, loaded.getStatus());

        // 3. Process payment
        Payment payment = Payment.builder()
                .order(loaded).method(PaymentMethod.CASH)
                .status(PaymentStatus.COMPLETED)
                .amount(BigDecimal.valueOf(110000))
                .tipAmount(BigDecimal.ZERO).refundedAmount(BigDecimal.ZERO)
                .amountTendered(BigDecimal.valueOf(120000))
                .changeDue(BigDecimal.valueOf(10000))
                .transactionId("POS-FLOW-TXN")
                .paidAt(OffsetDateTime.now(ZoneOffset.UTC))
                .completedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        loaded.addPayment(payment);
        loaded.setStatus(OrderStatus.DELIVERED);
        loaded.setPaymentStatus(PaymentStatus.COMPLETED);
        loaded.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        orderRepository.save(loaded);
        em.flush();
        em.clear();

        // 4. Verify final state
        Order finalOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.DELIVERED, finalOrder.getStatus());
        assertEquals(2, finalOrder.getItems().size());
        assertTrue(finalOrder.isFullyPaid());
        assertNotNull(finalOrder.getCompletedAt());

        // Payment persisted
        assertEquals(1, paymentRepository.findByOrderId(finalOrder.getId()).size());
    }

    @Test
    @DisplayName("POS flow with service fee and discount")
    void posFlow_withFeeAndDiscount() {
        Order order = Order.builder()
                .orderNumber("POS-FEE-001")
                .restaurant(restaurant)
                .diningTable(table)
                .status(OrderStatus.PREPARING)
                .orderType(OrderType.DINE_IN)
                .orderSource(OrderSource.ADMIN_PANEL)
                .subtotal(BigDecimal.valueOf(200000))
                .deliveryFee(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.valueOf(20000))
                .serviceFee(BigDecimal.valueOf(18000))
                .serviceFeePercent(BigDecimal.valueOf(10))
                .entryFee(BigDecimal.valueOf(5000))
                .total(BigDecimal.valueOf(203000)) // 200000 - 20000 + 18000 + 5000
                .grandTotal(BigDecimal.valueOf(203000))
                .items(new ArrayList<>())
                .build();

        order.addItem(OrderItem.builder()
                .productId(1L).productName("Combo").quantity(1)
                .unitPrice(BigDecimal.valueOf(200000)).totalPrice(BigDecimal.valueOf(200000)).build());

        order = orderRepository.save(order);
        em.flush();
        em.clear();

        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(0, BigDecimal.valueOf(200000).compareTo(reloaded.getSubtotal()));
        assertEquals(0, BigDecimal.valueOf(20000).compareTo(reloaded.getDiscount()));
        assertEquals(0, BigDecimal.valueOf(18000).compareTo(reloaded.getServiceFee()));
        assertEquals(0, BigDecimal.valueOf(5000).compareTo(reloaded.getEntryFee()));
        assertEquals(0, BigDecimal.valueOf(203000).compareTo(reloaded.getTotal()));
    }
}
