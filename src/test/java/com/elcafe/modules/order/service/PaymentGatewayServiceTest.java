package com.elcafe.modules.order.service;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private TransactionalOrderOperationService transactionalService;
    @Mock private OrderEventBroadcaster eventBroadcaster;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private PaymentGatewayService gatewayService;

    @Test
    @DisplayName("verifyPaymentStatus — returns status for existing payment")
    void verifyPaymentStatus_returnsStatus() {
        Payment payment = Payment.builder().id(1L).status(PaymentStatus.COMPLETED).build();
        when(paymentRepository.findByTransactionId("PI-001")).thenReturn(Optional.of(payment));

        String status = gatewayService.verifyPaymentStatus("PI-001");
        assertNotNull(status);
    }

    @Test
    @DisplayName("verifyPaymentStatus — not found throws")
    void verifyPaymentStatus_notFound_throws() {
        when(paymentRepository.findByTransactionId("NONE")).thenReturn(Optional.empty());

        assertThrows(Exception.class, () -> gatewayService.verifyPaymentStatus("NONE"));
    }
}
