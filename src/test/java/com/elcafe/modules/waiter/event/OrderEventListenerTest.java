package com.elcafe.modules.waiter.event;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.waiter.entity.OrderEvent;
import com.elcafe.modules.waiter.enums.OrderEventType;
import com.elcafe.modules.waiter.repository.OrderEventRepository;
import com.elcafe.modules.waiter.service.WaiterPerformanceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    @Mock private OrderEventRepository orderEventRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private WaiterPerformanceService performanceService;
    @Mock private EntityManager entityManager;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OrderEventListener listener;

    private Order order;

    @BeforeEach
    void setUp() {
        order = createOrder();
        Order orderRef = new Order();
        orderRef.setId(1L);
        lenient().when(entityManager.getReference(eq(Order.class), eq(1L))).thenReturn(orderRef);
    }

    // ==================== handleOrderCreated ====================

    @Test
    @DisplayName("handleOrderCreated saves audit trail")
    void handleOrderCreated_savesAuditTrail() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                this, 1L, "W001", 1L, 1L, "Ali", 3);

        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(i -> i.getArgument(0));

        listener.handleOrderCreated(event);

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventRepository).save(captor.capture());

        OrderEvent saved = captor.getValue();
        assertEquals(OrderEventType.ORDER_CREATED, saved.getEventType());
        assertEquals("Ali", saved.getTriggeredBy());
        assertNotNull(saved.getMetadata());
    }

    @Test
    @DisplayName("handleOrderCreated with null orderId skips audit trail")
    void handleOrderCreated_nullOrderId_skipsAudit() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                this, null, "W001", null, null, null, 0);

        listener.handleOrderCreated(event);

        verify(orderEventRepository, never()).save(any());
    }

    // ==================== handleOrderSubmitted ====================

    @Test
    @DisplayName("handleOrderSubmitted saves audit trail")
    void handleOrderSubmitted_savesAuditTrail() {
        OrderSubmittedEvent event = new OrderSubmittedEvent(
                this, 1L, "W001", 1L, 1L, "Ali", BigDecimal.valueOf(50), 2);

        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(i -> i.getArgument(0));

        listener.handleOrderSubmitted(event);

        verify(orderEventRepository).save(any(OrderEvent.class));
    }

    // ==================== handleOrderPaid ====================

    @Test
    @DisplayName("handleOrderPaid saves audit trail and updates performance")
    void handleOrderPaid_savesAuditAndUpdatesPerformance() {
        OrderPaidEvent event = new OrderPaidEvent(
                this, 1L, "W001", null, 1L, "Ali",
                BigDecimal.valueOf(100), "CASH", "TXN-001");

        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        listener.handleOrderPaid(event);

        verify(orderEventRepository).save(any(OrderEvent.class));
        verify(performanceService).recordOrderCompletion(eq(order), eq(1L), anyLong());
    }

    @Test
    @DisplayName("handleOrderPaid with null waiterId skips performance update")
    void handleOrderPaid_nullWaiterId_skipsPerformance() {
        OrderPaidEvent event = new OrderPaidEvent(
                this, 1L, "W001", null, null, null,
                BigDecimal.valueOf(100), "CASH", null);

        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(i -> i.getArgument(0));

        listener.handleOrderPaid(event);

        verify(performanceService, never()).recordOrderCompletion(any(), anyLong(), anyLong());
    }

    // ==================== handleItemRemoved ====================

    @Test
    @DisplayName("handleItemRemoved records void item for waiter performance")
    void handleItemRemoved_recordsVoidItem() {
        OrderItemRemovedEvent event = new OrderItemRemovedEvent(
                this, 1L, "W001", null, 1L, "Ali",
                "Espresso", "Customer changed mind", BigDecimal.valueOf(5000));

        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        listener.handleItemRemoved(event);

        verify(performanceService).recordVoidItem(eq(1L), anyLong(), eq(BigDecimal.valueOf(5000)));
    }

    // ==================== Error handling ====================

    @Test
    @DisplayName("handleOrderCreated catches and logs exceptions without rethrowing")
    void handleOrderCreated_exceptionCaught_doesNotRethrow() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                this, 1L, "W001", null, null, "Ali", 0);

        when(entityManager.getReference(eq(Order.class), eq(1L))).thenReturn(new Order());
        when(orderEventRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        // Should not throw — error is caught and logged
        assertDoesNotThrow(() -> listener.handleOrderCreated(event));
    }

    // ==================== createAuditTrail metadata ====================

    @Test
    @DisplayName("Audit trail includes serialized metadata")
    void auditTrail_includesSerializedMetadata() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                this, 1L, "W001", 5L, 1L, "Ali", 3);

        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(i -> i.getArgument(0));

        listener.handleOrderCreated(event);

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventRepository).save(captor.capture());

        String metadata = captor.getValue().getMetadata();
        assertNotNull(metadata);
        assertTrue(metadata.contains("W001") || metadata.contains("orderId"));
    }
}
