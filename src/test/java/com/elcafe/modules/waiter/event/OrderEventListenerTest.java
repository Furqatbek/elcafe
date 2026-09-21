package com.elcafe.modules.waiter.event;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.waiter.entity.OrderEvent;
import com.elcafe.modules.waiter.repository.OrderEventRepository;
import com.elcafe.modules.waiter.service.WaiterPerformanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderEventListenerTest {

    @Mock private OrderEventRepository orderEventRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private WaiterPerformanceService performanceService;
    @Mock private EntityManager entityManager;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private OrderEventListener listener;

    @Test @DisplayName("handleOrderCreated saves audit") void handleCreated() {
        Order ref = createOrder(1L, OrderStatus.NEW);
        when(entityManager.getReference(eq(Order.class), eq(1L))).thenReturn(ref);
        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(i -> i.getArgument(0));

        OrderCreatedEvent event = new OrderCreatedEvent(this, 1L, "W001", 1L, 1L, "Ali", 3);
        assertDoesNotThrow(() -> listener.handleOrderCreated(event));
        verify(orderEventRepository).save(any(OrderEvent.class));
    }

    @Test @DisplayName("handleOrderCreated with null orderId skips") void handleCreated_nullId() {
        OrderCreatedEvent event = new OrderCreatedEvent(this, null, "W001", null, null, "Ali", 0);
        assertDoesNotThrow(() -> listener.handleOrderCreated(event));
    }
}
