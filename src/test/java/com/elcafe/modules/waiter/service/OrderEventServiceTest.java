package com.elcafe.modules.waiter.service;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.waiter.dto.OrderEventResponse;
import com.elcafe.modules.waiter.entity.OrderEvent;
import com.elcafe.modules.waiter.enums.OrderEventType;
import com.elcafe.modules.waiter.repository.OrderEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderEventServiceTest {

    @Mock
    private OrderEventRepository orderEventRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private OrderEventService orderEventService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = createOrder();
    }

    @Test
    @DisplayName("publishEvent with metadata serializes and saves")
    void publishEvent_withMetadata_serializesAndSaves() {
        Map<String, Object> metadata = Map.of("itemsAdded", 3, "action", "add");
        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(i -> {
            OrderEvent e = i.getArgument(0);
            e.setId(1L);
            return e;
        });

        OrderEvent result = orderEventService.publishEvent(order, OrderEventType.ORDER_UPDATED, "Ali", metadata);

        assertNotNull(result);
        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventRepository).save(captor.capture());

        OrderEvent saved = captor.getValue();
        assertEquals(OrderEventType.ORDER_UPDATED, saved.getEventType());
        assertEquals("Ali", saved.getTriggeredBy());
        assertNotNull(saved.getMetadata());
    }

    @Test
    @DisplayName("publishEvent with null metadata saves without metadata")
    void publishEvent_nullMetadata_savesWithoutMetadata() {
        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(i -> {
            OrderEvent e = i.getArgument(0);
            e.setId(1L);
            return e;
        });

        orderEventService.publishEvent(order, OrderEventType.ORDER_CREATED, "Ali", null);

        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventRepository).save(captor.capture());
        assertNull(captor.getValue().getMetadata());
    }

    @Test
    @DisplayName("recordEvent delegates to publishEvent with null metadata")
    void recordEvent_delegatesToPublishEvent() {
        when(orderEventRepository.save(any(OrderEvent.class))).thenAnswer(i -> {
            OrderEvent e = i.getArgument(0);
            e.setId(1L);
            return e;
        });

        OrderEvent result = orderEventService.recordEvent(order, OrderEventType.ORDER_CLOSED, "Ali");

        assertNotNull(result);
        ArgumentCaptor<OrderEvent> captor = ArgumentCaptor.forClass(OrderEvent.class);
        verify(orderEventRepository).save(captor.capture());
        assertEquals(OrderEventType.ORDER_CLOSED, captor.getValue().getEventType());
        assertNull(captor.getValue().getMetadata());
    }

    @Test
    @DisplayName("getOrderHistory returns converted events")
    void getOrderHistory_returnsConvertedEvents() {
        OrderEvent event1 = OrderEvent.builder()
                .id(1L).order(order).eventType(OrderEventType.ORDER_CREATED)
                .triggeredBy("Ali").createdAt(LocalDateTime.now()).build();
        OrderEvent event2 = OrderEvent.builder()
                .id(2L).order(order).eventType(OrderEventType.ORDER_UPDATED)
                .triggeredBy("Ali").createdAt(LocalDateTime.now()).build();

        when(orderEventRepository.findByOrderIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of(event1, event2));

        List<OrderEventResponse> result = orderEventService.getOrderHistory(1L);

        assertEquals(2, result.size());
        assertEquals(OrderEventType.ORDER_CREATED, result.get(0).getEventType());
        assertEquals(OrderEventType.ORDER_UPDATED, result.get(1).getEventType());
    }

    @Test
    @DisplayName("getOrderHistory with no events returns empty list")
    void getOrderHistory_noEvents_returnsEmpty() {
        when(orderEventRepository.findByOrderIdOrderByCreatedAtDesc(99L))
                .thenReturn(List.of());

        List<OrderEventResponse> result = orderEventService.getOrderHistory(99L);

        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("getEventsByType returns filtered events")
    void getEventsByType_returnsFiltered() {
        OrderEvent event = OrderEvent.builder()
                .id(1L).order(order).eventType(OrderEventType.ORDER_CREATED)
                .triggeredBy("Ali").createdAt(LocalDateTime.now()).build();

        when(orderEventRepository.findByEventTypeOrderByCreatedAtDesc(OrderEventType.ORDER_CREATED))
                .thenReturn(List.of(event));

        List<OrderEventResponse> result = orderEventService.getEventsByType(OrderEventType.ORDER_CREATED);

        assertEquals(1, result.size());
        assertEquals(OrderEventType.ORDER_CREATED, result.get(0).getEventType());
    }

    @Test
    @DisplayName("getWaiterEvents returns events within date range")
    void getWaiterEvents_returnsEventsInRange() {
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();

        OrderEvent event = OrderEvent.builder()
                .id(1L).order(order).eventType(OrderEventType.ORDER_UPDATED)
                .triggeredBy("Ali").createdAt(LocalDateTime.now().minusDays(1)).build();

        when(orderEventRepository.findByWaiterAndDateRange("Ali", start, end))
                .thenReturn(List.of(event));

        List<OrderEventResponse> result = orderEventService.getWaiterEvents("Ali", start, end);

        assertEquals(1, result.size());
        assertEquals("Ali", result.get(0).getTriggeredBy());
    }
}
