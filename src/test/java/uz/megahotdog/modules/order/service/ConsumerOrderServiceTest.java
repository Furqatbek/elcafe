package uz.megahotdog.modules.order.service;

import uz.megahotdog.modules.customer.entity.Customer;
import uz.megahotdog.modules.customer.repository.CustomerRepository;
import uz.megahotdog.modules.menu.entity.Product;
import uz.megahotdog.modules.menu.repository.ProductRepository;
import uz.megahotdog.modules.notification.service.NotificationService;
import uz.megahotdog.modules.order.dto.consumer.CreateOrderRequest;
import uz.megahotdog.modules.order.dto.consumer.OrderResponse;
import uz.megahotdog.modules.order.entity.Order;
import uz.megahotdog.modules.order.enums.OrderStatus;
import uz.megahotdog.modules.order.repository.OrderRepository;
import uz.megahotdog.modules.promotion.service.CouponValidationService;
import uz.megahotdog.modules.promotion.service.DiscountCalculationService;
import uz.megahotdog.modules.restaurant.entity.Restaurant;
import uz.megahotdog.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static uz.megahotdog.modules.waiter.helper.TestDataFactory.createCustomer;
import static uz.megahotdog.modules.waiter.helper.TestDataFactory.createOrder;
import static uz.megahotdog.modules.waiter.helper.TestDataFactory.createProduct;
import static uz.megahotdog.modules.waiter.helper.TestDataFactory.createRestaurant;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsumerOrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private ProductRepository productRepository;
    @Mock private NotificationService notificationService;
    @Mock private CouponValidationService couponValidationService;
    @Mock private DiscountCalculationService discountCalculationService;

    @InjectMocks private ConsumerOrderService consumerOrderService;

    @Test
    @DisplayName("getOrderByNumber — returns order response")
    void getOrderByNumber_returnsResponse() {
        Order order = createOrder(1L, OrderStatus.PREPARING);
        when(orderRepository.findByOrderNumber("ORD-001")).thenReturn(Optional.of(order));

        OrderResponse result = consumerOrderService.getOrderByNumber("ORD-001");

        assertNotNull(result);
    }

    @Test
    @DisplayName("getOrderByNumber — not found throws")
    void getOrderByNumber_notFound_throws() {
        when(orderRepository.findByOrderNumber("NONE")).thenReturn(Optional.empty());

        assertThrows(Exception.class, () -> consumerOrderService.getOrderByNumber("NONE"));
    }

    @Test
    @DisplayName("cancelOrder — cancels existing order")
    void cancelOrder_cancels() {
        Order order = createOrder(1L, OrderStatus.NEW);
        when(orderRepository.findByOrderNumber("ORD-001")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderResponse result = consumerOrderService.cancelOrder("ORD-001", "Changed mind");

        assertNotNull(result);
    }
}
