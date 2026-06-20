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
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.enums.WaiterRole;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class POSPaymentIntegrationTest {

    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private RestaurantTable table;
    private Order order;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setCity("Tashkent");
        restaurant.setPhone("+998901234567");
        restaurant.setEmail("test@restaurant.com");
        restaurant.setActive(true);
        restaurant.setAcceptingOrders(true);
        restaurant.setDeliveryFee(BigDecimal.ZERO);
        em.persist(restaurant);

        table = new RestaurantTable();
        table.setRestaurant(restaurant);
        table.setTableNumber("T1");
        table.setTableName("Table 1");
        table.setStatus(TableStatus.OCCUPIED);
        table.setCapacity(4);
        table.setActive(true);
        em.persist(table);

        Waiter waiter = new Waiter();
        waiter.setRestaurantId(1L);
        waiter.setName("Ali");
        waiter.setPinCode("1234");
        waiter.setRole(WaiterRole.WAITER);
        waiter.setActive(true);
        em.persist(waiter);

        order = Order.builder()
                .orderNumber("PAY-TEST-001")
                .restaurant(restaurant)
                .diningTable(table)
                .waiter(waiter)
                .status(OrderStatus.PREPARING)
                .orderType(OrderType.DINE_IN)
                .orderSource(OrderSource.WAITER)
                .subtotal(BigDecimal.valueOf(100000))
                .deliveryFee(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .total(BigDecimal.valueOf(100000))
                .grandTotal(BigDecimal.valueOf(100000))
                .items(new ArrayList<>())
                .build();

        OrderItem item = OrderItem.builder()
                .productId(1L).productName("Steak").quantity(1)
                .unitPrice(BigDecimal.valueOf(100000)).totalPrice(BigDecimal.valueOf(100000))
                .build();
        order.addItem(item);

        order = orderRepository.save(order);
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Full payment persists and links to order")
    void fullPayment_persistsAndLinksToOrder() {
        Order loaded = orderRepository.findById(order.getId()).orElseThrow();

        Payment payment = Payment.builder()
                .order(loaded)
                .method(PaymentMethod.CASH)
                .status(PaymentStatus.COMPLETED)
                .amount(BigDecimal.valueOf(100000))
                .tipAmount(BigDecimal.ZERO)
                .refundedAmount(BigDecimal.ZERO)
                .amountTendered(BigDecimal.valueOf(120000))
                .changeDue(BigDecimal.valueOf(20000))
                .transactionId("CASH-001")
                .paidAt(OffsetDateTime.now(ZoneOffset.UTC))
                .completedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        paymentRepository.save(payment);
        em.flush();
        em.clear();

        // Reload and verify
        List<Payment> payments = paymentRepository.findByOrderId(loaded.getId());
        assertEquals(1, payments.size());

        Payment reloaded = payments.get(0);
        assertEquals(PaymentMethod.CASH, reloaded.getMethod());
        assertEquals(PaymentStatus.COMPLETED, reloaded.getStatus());
        assertEquals(0, BigDecimal.valueOf(100000).compareTo(reloaded.getAmount()));
        assertEquals(0, BigDecimal.valueOf(20000).compareTo(reloaded.getChangeDue()));
        assertEquals("CASH-001", reloaded.getTransactionId());
        assertEquals(loaded.getId(), reloaded.getOrder().getId());
    }

    @Test
    @DisplayName("Split payment — two payments for one order")
    void splitPayment_twoPaymentsPersisted() {
        Order loaded = orderRepository.findById(order.getId()).orElseThrow();

        Payment payment1 = Payment.builder()
                .order(loaded).method(PaymentMethod.CASH)
                .status(PaymentStatus.COMPLETED)
                .amount(BigDecimal.valueOf(60000))
                .tipAmount(BigDecimal.ZERO).refundedAmount(BigDecimal.ZERO)
                .transactionId("SPLIT-001").splitNumber(1)
                .paidAt(OffsetDateTime.now(ZoneOffset.UTC))
                .completedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        Payment payment2 = Payment.builder()
                .order(loaded).method(PaymentMethod.CARD)
                .status(PaymentStatus.COMPLETED)
                .amount(BigDecimal.valueOf(40000))
                .tipAmount(BigDecimal.ZERO).refundedAmount(BigDecimal.ZERO)
                .transactionId("SPLIT-002").splitNumber(2)
                .paidAt(OffsetDateTime.now(ZoneOffset.UTC))
                .completedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        paymentRepository.save(payment1);
        paymentRepository.save(payment2);
        em.flush();
        em.clear();

        List<Payment> payments = paymentRepository.findByOrderId(loaded.getId());
        assertEquals(2, payments.size());

        BigDecimal totalPaid = payments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, BigDecimal.valueOf(100000).compareTo(totalPaid));
    }

    @Test
    @DisplayName("Payment with tip — tip persists correctly")
    void paymentWithTip_tipPersists() {
        Order loaded = orderRepository.findById(order.getId()).orElseThrow();

        Payment payment = Payment.builder()
                .order(loaded).method(PaymentMethod.CARD)
                .status(PaymentStatus.COMPLETED)
                .amount(BigDecimal.valueOf(100000))
                .tipAmount(BigDecimal.valueOf(10000))
                .refundedAmount(BigDecimal.ZERO)
                .transactionId("TIP-001")
                .paidAt(OffsetDateTime.now(ZoneOffset.UTC))
                .completedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        paymentRepository.save(payment);
        em.flush();
        em.clear();

        Payment reloaded = paymentRepository.findByOrderId(loaded.getId()).get(0);
        assertEquals(0, BigDecimal.valueOf(10000).compareTo(reloaded.getTipAmount()));
        assertEquals(0, BigDecimal.valueOf(110000).compareTo(reloaded.getTotalWithTip()));
    }

    @Test
    @DisplayName("Order.isFullyPaid works after payment persistence")
    void orderIsFullyPaid_afterPayment() {
        Order loaded = orderRepository.findById(order.getId()).orElseThrow();

        assertFalse(loaded.isFullyPaid(), "Order should NOT be fully paid before payment");

        Payment payment = Payment.builder()
                .order(loaded).method(PaymentMethod.CASH)
                .status(PaymentStatus.COMPLETED)
                .amount(BigDecimal.valueOf(100000))
                .tipAmount(BigDecimal.ZERO).refundedAmount(BigDecimal.ZERO)
                .transactionId("FULL-001")
                .paidAt(OffsetDateTime.now(ZoneOffset.UTC))
                .completedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        loaded.addPayment(payment);
        orderRepository.save(loaded);
        em.flush();
        em.clear();

        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
        assertTrue(reloaded.isFullyPaid(), "Order should be fully paid after full payment");
    }

    @Test
    @DisplayName("Partial payment — order NOT fully paid")
    void partialPayment_orderNotFullyPaid() {
        Order loaded = orderRepository.findById(order.getId()).orElseThrow();

        Payment payment = Payment.builder()
                .order(loaded).method(PaymentMethod.CASH)
                .status(PaymentStatus.COMPLETED)
                .amount(BigDecimal.valueOf(50000))
                .tipAmount(BigDecimal.ZERO).refundedAmount(BigDecimal.ZERO)
                .transactionId("PARTIAL-001")
                .paidAt(OffsetDateTime.now(ZoneOffset.UTC))
                .completedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        loaded.addPayment(payment);
        orderRepository.save(loaded);
        em.flush();
        em.clear();

        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
        assertFalse(reloaded.isFullyPaid(), "Order should NOT be fully paid after partial payment");
        assertEquals(0, BigDecimal.valueOf(50000).compareTo(reloaded.getRemainingBalance()));
    }

    @Test
    @DisplayName("Payment data survives full round-trip")
    void paymentData_survivesRoundTrip() {
        Order loaded = orderRepository.findById(order.getId()).orElseThrow();

        Payment payment = Payment.builder()
                .order(loaded).method(PaymentMethod.CARD)
                .status(PaymentStatus.COMPLETED)
                .amount(BigDecimal.valueOf(95000))
                .tipAmount(BigDecimal.valueOf(5000))
                .refundedAmount(BigDecimal.ZERO)
                .amountTendered(null)
                .changeDue(BigDecimal.ZERO)
                .transactionId("ROUND-TRIP-001")
                .paymentGateway("STRIPE")
                .paymentDetails("{\"last4\":\"4242\"}")
                .processedBy("admin@test.com")
                .splitNumber(1)
                .paidAt(OffsetDateTime.now(ZoneOffset.UTC))
                .completedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        paymentRepository.save(payment);
        em.flush();
        em.clear();

        Payment reloaded = paymentRepository.findByOrderId(loaded.getId()).get(0);
        assertEquals(PaymentMethod.CARD, reloaded.getMethod());
        assertEquals(0, BigDecimal.valueOf(95000).compareTo(reloaded.getAmount()));
        assertEquals(0, BigDecimal.valueOf(5000).compareTo(reloaded.getTipAmount()));
        assertEquals("ROUND-TRIP-001", reloaded.getTransactionId());
        assertEquals("STRIPE", reloaded.getPaymentGateway());
        assertEquals("{\"last4\":\"4242\"}", reloaded.getPaymentDetails());
        assertEquals("admin@test.com", reloaded.getProcessedBy());
        assertEquals(1, reloaded.getSplitNumber());
        assertNotNull(reloaded.getPaidAt());
        assertNotNull(reloaded.getCompletedAt());
    }
}
