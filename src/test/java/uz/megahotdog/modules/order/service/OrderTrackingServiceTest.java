package uz.megahotdog.modules.order.service;

import uz.megahotdog.exception.ResourceNotFoundException;
import uz.megahotdog.modules.order.dto.OrderTrackingResponse;
import uz.megahotdog.modules.order.entity.Order;
import uz.megahotdog.modules.order.enums.OrderStatus;
import uz.megahotdog.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static uz.megahotdog.modules.waiter.helper.TestDataFactory.createOrder;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderTrackingServiceTest {

    @Mock private OrderRepository orderRepository;
    @InjectMocks private OrderTrackingService trackingService;

    @Test
    @DisplayName("getOrderTracking — returns tracking for valid order")
    void getOrderTracking_success() {
        Order order = createOrder(1L, OrderStatus.PREPARING);
        when(orderRepository.findByOrderNumber("ORD-001")).thenReturn(Optional.of(order));
        OrderTrackingResponse result = trackingService.getOrderTracking("ORD-001");
        assertNotNull(result);
    }

    @Test
    @DisplayName("getOrderTracking — not found throws")
    void getOrderTracking_notFound_throws() {
        when(orderRepository.findByOrderNumber("NONE")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> trackingService.getOrderTracking("NONE"));
    }

    @Test
    @DisplayName("getRecentOrdersByPhone — returns list")
    void getRecentOrdersByPhone_returnsList() {
        when(orderRepository.findByCustomerPhoneAndCreatedAtAfterWithDetails(anyString(), any()))
                .thenReturn(List.of(createOrder(1L, OrderStatus.COMPLETED)));
        List<OrderTrackingResponse> result = trackingService.getRecentOrdersByPhone("+998901111111");
        assertNotNull(result);
    }
}
