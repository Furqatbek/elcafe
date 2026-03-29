package com.elcafe.modules.order.service;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.notification.service.NotificationService;
import com.elcafe.modules.order.dto.consumer.CreateOrderRequest;
import com.elcafe.modules.order.dto.consumer.OrderResponse;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.promotion.service.CouponValidationService;
import com.elcafe.modules.promotion.service.DiscountCalculationService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createCustomer;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createProduct;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createRestaurant;
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
