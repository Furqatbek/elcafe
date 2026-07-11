package com.elcafe.modules.marketing.event;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.service.OrderService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the DEFAULT of the order-completion chain (audit FUNC-15): with no
 * {@code ORDER_COMPLETED_EVENTS_ENABLED} configured, a fully qualifying completion publishes
 * NOTHING. Loyalty bonuses must never start flowing because of a deploy — only because someone
 * deliberately set the flag.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@RecordApplicationEvents
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:occdefault;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=VALUE;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS public",
        "management.health.redis.enabled=false",
        // Deliberately NOT setting app.marketing.order-completed-events.enabled.
})
class OrderCompletedEventDefaultOffTest {

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private RestaurantRepository restaurantRepository;
    @Autowired private CustomerRepository customerRepository;

    @Test
    @DisplayName("default config: a fully qualifying completion publishes no OrderCompletedEvent")
    void defaultOffPublishesNothing(ApplicationEvents events) {
        Restaurant restaurant = restaurantRepository.save(
                Restaurant.builder().name("Dark Cafe").address("1 Dark St").active(true).build());
        Customer customer = new Customer();
        customer.setRestaurantId(restaurant.getId());
        customer.setFirstName("Dark");
        customer.setLastName("Launch");
        customer.setPhone("+998900020001");
        customer.setQrCode("occ-default-qr-1");
        customer = customerRepository.save(customer);

        Order order = Order.builder()
                .orderNumber("OCC-DARK-1")
                .restaurant(restaurant)
                .customer(customer)
                .status(OrderStatus.PICKED_UP)
                .subtotal(new BigDecimal("50"))
                .deliveryFee(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .total(new BigDecimal("50"))
                .items(new ArrayList<>())
                .build();
        order.addPayment(Payment.builder()
                .method(PaymentMethod.CASH).status(PaymentStatus.COMPLETED)
                .amount(new BigDecimal("50")).tipAmount(BigDecimal.ZERO)
                .refundedAmount(BigDecimal.ZERO)
                .build());
        order = orderRepository.save(order);

        orderService.updateOrderStatus(order.getId(), OrderStatus.COMPLETED, null, "TEST");

        assertThat(events.stream(OrderCompletedEvent.class)).isEmpty();
    }
}
