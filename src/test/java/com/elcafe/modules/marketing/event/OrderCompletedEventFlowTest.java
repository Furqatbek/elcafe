package com.elcafe.modules.marketing.event;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.dto.UpdatePaymentRequest;
import com.elcafe.modules.order.service.OrderService;
import com.elcafe.modules.order.service.PaymentService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration pin for the activated order-completion chain (audit FUNC-15) driven through the REAL
 * status-transition path: with {@code app.marketing.order-completed-events.enabled=true}, an order
 * that becomes settled while fully paid publishes exactly one {@link OrderCompletedEvent} — and the
 * disqualified variants (walk-in, unpaid, already-settled re-transition) publish none.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RecordApplicationEvents
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:occflow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=VALUE;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS public",
        "management.health.redis.enabled=false",
        "app.marketing.order-completed-events.enabled=true",
})
class OrderCompletedEventFlowTest {

    @Autowired private OrderService orderService;
    @Autowired private PaymentService paymentService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private CustomerRepository customerRepository;

    private Restaurant restaurant;

    @BeforeAll
    void seed() {
        restaurant = restaurantRepository.save(
                Restaurant.builder().name("Event Cafe").address("1 Event St").active(true).build());
    }

    private Customer customer(String phone, String qr) {
        Customer c = new Customer();
        c.setRestaurantId(restaurant.getId());
        c.setFirstName("Eve");
        c.setLastName("Nt");
        c.setPhone(phone);
        c.setQrCode(qr);
        return customerRepository.save(c);
    }

    private Order order(String number, OrderStatus status, Customer customer, boolean paid) {
        Order order = Order.builder()
                .orderNumber(number)
                .restaurant(restaurant)
                .customer(customer)
                .status(status)
                .subtotal(new BigDecimal("50"))
                .deliveryFee(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .total(new BigDecimal("50"))
                .items(new ArrayList<>())
                .build();
        if (paid) {
            Payment payment = Payment.builder()
                    .method(PaymentMethod.CASH).status(PaymentStatus.COMPLETED)
                    .amount(new BigDecimal("50")).tipAmount(BigDecimal.ZERO)
                    .refundedAmount(BigDecimal.ZERO)
                    .build();
            order.addPayment(payment);
        }
        return orderRepository.save(order);
    }

    private List<OrderCompletedEvent> completedEvents(ApplicationEvents events) {
        return events.stream(OrderCompletedEvent.class).toList();
    }

    @Test
    @DisplayName("paid order with customer completes → exactly one event, isFirstOrder=true")
    void qualifyingCompletionPublishesOnce(ApplicationEvents events) {
        Customer c = customer("+998900010001", "occ-qr-1");
        Order o = order("OCC-1", OrderStatus.PICKED_UP, c, true);

        orderService.updateOrderStatus(o.getId(), OrderStatus.COMPLETED, null, "TEST");

        List<OrderCompletedEvent> published = completedEvents(events);
        assertThat(published).hasSize(1);
        assertThat(published.get(0).getCustomer().getId()).isEqualTo(c.getId());
        assertThat(published.get(0).isFirstOrder()).isTrue();
        assertThat(published.get(0).getOrderTotal()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("customer with an earlier settled order → isFirstOrder=false")
    void repeatCustomerIsNotFirstOrder(ApplicationEvents events) {
        Customer c = customer("+998900010002", "occ-qr-2");
        order("OCC-2a", OrderStatus.COMPLETED, c, true); // history
        Order o = order("OCC-2b", OrderStatus.PICKED_UP, c, true);

        orderService.updateOrderStatus(o.getId(), OrderStatus.COMPLETED, null, "TEST");

        List<OrderCompletedEvent> published = completedEvents(events);
        assertThat(published).hasSize(1);
        assertThat(published.get(0).isFirstOrder()).isFalse();
    }

    @Test
    @DisplayName("walk-in order (no customer) completes → no event")
    void walkInPublishesNothing(ApplicationEvents events) {
        Order o = order("OCC-3", OrderStatus.PICKED_UP, null, true);

        orderService.updateOrderStatus(o.getId(), OrderStatus.COMPLETED, null, "TEST");

        assertThat(completedEvents(events)).isEmpty();
    }

    @Test
    @DisplayName("unpaid order completes → no event (accrual only on money received)")
    void unpaidPublishesNothing(ApplicationEvents events) {
        Customer c = customer("+998900010003", "occ-qr-3");
        Order o = order("OCC-4", OrderStatus.PICKED_UP, c, false);

        orderService.updateOrderStatus(o.getId(), OrderStatus.COMPLETED, null, "TEST");

        assertThat(completedEvents(events)).isEmpty();
    }

    @Test
    @DisplayName("order whose event already fired (marker set) re-transitions → no second event")
    void firedOrderReTransitionPublishesNothing(ApplicationEvents events) {
        Customer c = customer("+998900010004", "occ-qr-4");
        Order o = order("OCC-5", OrderStatus.DELIVERED, c, true);
        // Simulate the event having fired at the true qualifying moment (as PaymentService would).
        o.setCompletionEventPublishedAt(OffsetDateTime.now(ZoneOffset.UTC));
        orderRepository.save(o);

        orderService.updateOrderStatus(o.getId(), OrderStatus.COMPLETED, null, "TEST");

        assertThat(completedEvents(events)).isEmpty();
    }

    @Test
    @DisplayName("settled unpaid order becomes paid via the admin payment CRUD → fires once there, "
            + "and the later status transition cannot fire it again")
    void adminPaymentReconciliationFiresOnceAcrossSites(ApplicationEvents events) {
        Customer c = customer("+998900010005", "occ-qr-5");
        // COD-style: delivered but unpaid, with a PENDING gateway payment awaiting reconciliation.
        Order o = order("OCC-6", OrderStatus.DELIVERED, c, false);
        Payment pending = Payment.builder()
                .method(PaymentMethod.CARD).status(PaymentStatus.PENDING)
                .amount(new BigDecimal("50")).tipAmount(BigDecimal.ZERO)
                .refundedAmount(BigDecimal.ZERO)
                .build();
        o.addPayment(pending);
        o = orderRepository.save(o);
        Long paymentId = o.getPayments().get(0).getId();

        // Admin reconciles the payment — the previously-missed qualifying moment (review finding).
        UpdatePaymentRequest reconcile = new UpdatePaymentRequest();
        reconcile.setStatus(PaymentStatus.COMPLETED);
        paymentService.updatePayment(o.getId(), paymentId, reconcile);

        assertThat(completedEvents(events)).hasSize(1);

        // The later PATCH to COMPLETED must not fire a second event (durable marker).
        orderService.updateOrderStatus(o.getId(), OrderStatus.COMPLETED, null, "TEST");
        assertThat(completedEvents(events)).hasSize(1);
    }
}
