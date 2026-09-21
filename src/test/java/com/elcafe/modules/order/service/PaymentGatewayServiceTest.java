package com.elcafe.modules.order.service;

import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private TransactionalOrderOperationService transactionalService;
    @Mock private OrderEventBroadcaster eventBroadcaster;
    @Mock private ApplicationEventPublisher eventPublisher;
    @InjectMocks private PaymentGatewayService gatewayService;

    @Test @DisplayName("verifyPaymentStatus returns succeeded (mock impl)")
    void verifyStatus_returnsMock() {
        // Current impl always returns "succeeded" (TODO in source)
        String status = gatewayService.verifyPaymentStatus("PI-001");
        assertEquals("succeeded", status);
    }
}
