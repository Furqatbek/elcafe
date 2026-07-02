package com.elcafe.modules.order.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.order.dto.OrderTrackingResponse;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderTrackingServiceTest {

    @Mock private OrderRepository orderRepository;
    @InjectMocks private OrderTrackingService trackingService;

    private static final String TOKEN = "secret-tracking-token";

    private Order trackedOrder() {
        Order order = createOrder(1L, OrderStatus.PREPARING);
        order.setTrackingToken(TOKEN);
        return order;
    }

    @Test
    @DisplayName("getOrderTracking — returns tracking when the token matches")
    void getOrderTracking_validToken() {
        when(orderRepository.findByOrderNumber("ORD-001")).thenReturn(Optional.of(trackedOrder()));
        OrderTrackingResponse result = trackingService.getOrderTracking("ORD-001", TOKEN);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getOrderTracking — wrong token is treated as not-found (no enumeration)")
    void getOrderTracking_wrongToken_throws() {
        when(orderRepository.findByOrderNumber("ORD-001")).thenReturn(Optional.of(trackedOrder()));
        assertThrows(ResourceNotFoundException.class,
                () -> trackingService.getOrderTracking("ORD-001", "guessed"));
    }

    @Test
    @DisplayName("getOrderTracking — null token is rejected")
    void getOrderTracking_nullToken_throws() {
        when(orderRepository.findByOrderNumber("ORD-001")).thenReturn(Optional.of(trackedOrder()));
        assertThrows(ResourceNotFoundException.class,
                () -> trackingService.getOrderTracking("ORD-001", null));
    }

    @Test
    @DisplayName("getOrderTracking — unknown order throws")
    void getOrderTracking_notFound_throws() {
        when(orderRepository.findByOrderNumber("NONE")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> trackingService.getOrderTracking("NONE", "any"));
    }

    @Test
    @DisplayName("calculateETA — token-gated like tracking")
    void calculateETA_validToken() {
        when(orderRepository.findByOrderNumber("ORD-001")).thenReturn(Optional.of(trackedOrder()));
        assertNotNull(trackingService.calculateETA("ORD-001", TOKEN));
    }
}
