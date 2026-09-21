package com.elcafe.modules.order.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
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

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class PaymentRepositoryTest {

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private EntityManager em;

    private Order order;

    @BeforeEach
    void setUp() {
        Restaurant restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);

        order = Order.builder()
                .restaurant(restaurant)
                .orderNumber("ORD-PAY-001")
                .status(OrderStatus.COMPLETED)
                .subtotal(new BigDecimal("100.00"))
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .deliveryFee(BigDecimal.ZERO)
                .total(new BigDecimal("100.00"))
                .build();
        em.persist(order);
    }

    private Payment createPayment(PaymentStatus status, BigDecimal amount, BigDecimal tip) {
        Payment payment = Payment.builder()
                .order(order)
                .method(PaymentMethod.CASH)
                .status(status)
                .amount(amount)
                .tipAmount(tip)
                .build();
        em.persist(payment);
        return payment;
    }

    @Test
    @DisplayName("sumCompletedPaymentsByOrderId sums only COMPLETED payment amounts")
    void sumCompletedPaymentsByOrderId() {
        createPayment(PaymentStatus.COMPLETED, new BigDecimal("50.00"), BigDecimal.ZERO);
        createPayment(PaymentStatus.COMPLETED, new BigDecimal("30.00"), BigDecimal.ZERO);
        // PENDING payment should not be included
        createPayment(PaymentStatus.PENDING, new BigDecimal("20.00"), BigDecimal.ZERO);

        em.flush();
        em.clear();

        BigDecimal sum = paymentRepository.sumCompletedPaymentsByOrderId(order.getId());
        assertEquals(0, new BigDecimal("80.00").compareTo(sum));
    }

    @Test
    @DisplayName("countByStatus counts payments with given status")
    void countByStatus() {
        createPayment(PaymentStatus.COMPLETED, BigDecimal.TEN, BigDecimal.ZERO);
        createPayment(PaymentStatus.COMPLETED, BigDecimal.TEN, BigDecimal.ZERO);
        createPayment(PaymentStatus.PENDING, BigDecimal.TEN, BigDecimal.ZERO);

        em.flush();
        em.clear();

        assertEquals(2, paymentRepository.countByStatus(PaymentStatus.COMPLETED));
        assertEquals(1, paymentRepository.countByStatus(PaymentStatus.PENDING));
    }

    @Test
    @DisplayName("sumTipsByOrderId sums tips for completed payments only")
    void sumTipsByOrderId() {
        createPayment(PaymentStatus.COMPLETED, new BigDecimal("50.00"), new BigDecimal("5.00"));
        createPayment(PaymentStatus.COMPLETED, new BigDecimal("30.00"), new BigDecimal("3.00"));
        // PENDING tip should not be included
        createPayment(PaymentStatus.PENDING, new BigDecimal("20.00"), new BigDecimal("10.00"));

        em.flush();
        em.clear();

        BigDecimal tipSum = paymentRepository.sumTipsByOrderId(order.getId());
        assertEquals(0, new BigDecimal("8.00").compareTo(tipSum));
    }
}
