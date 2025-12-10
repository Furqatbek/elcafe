package com.elcafe.modules.order.service;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.notification.service.NotificationService;
import com.elcafe.modules.order.dto.consumer.CreateOrderRequest;
import com.elcafe.modules.order.dto.consumer.OrderResponse;
import com.elcafe.modules.order.entity.*;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumerOrderService {

    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;

    @Transactional
    public OrderResponse placeOrder(CreateOrderRequest request) {
        // 1. Validate restaurant
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        if (!restaurant.getActive()) {
            throw new RuntimeException("Restaurant is not active");
        }

        if (!restaurant.getAcceptingOrders()) {
            throw new RuntimeException("Restaurant is not accepting orders");
        }

        // 2. Find or create customer
        Customer customer = findOrCreateCustomer(request.getCustomerInfo());

        // 3. Build order
        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .restaurant(restaurant)
                .customer(customer)
                .status(OrderStatus.NEW)
                .orderSource(request.getOrderSource())
                .customerNotes(request.getCustomerNotes())
                .scheduledFor(request.getScheduledFor())
                .items(new ArrayList<>())
                .statusHistory(new ArrayList<>())
                .build();

        // 4. Add order items
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CreateOrderRequest.OrderItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new RuntimeException("Product not found: " + itemRequest.getProductId()));

            if (!product.getInStock()) {
                throw new RuntimeException("Product not available: " + product.getName());
            }

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(product.getPrice())
                    .totalPrice(product.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity())))
                    .specialInstructions(itemRequest.getSpecialInstructions())
                    .build();

            order.addItem(orderItem);
            subtotal = subtotal.add(orderItem.getTotalPrice());
        }

        // 5. Calculate costs
        BigDecimal deliveryFee = restaurant.getDeliveryFee() != null ? restaurant.getDeliveryFee() : BigDecimal.ZERO;
        BigDecimal tax = subtotal.multiply(BigDecimal.valueOf(0.10)); // 10% tax
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal total = subtotal.add(deliveryFee).add(tax).subtract(discount);

        order.setSubtotal(subtotal);
        order.setDeliveryFee(deliveryFee);
        order.setTax(tax);
        order.setDiscount(discount);
        order.setTotal(total);

        // 6. Add delivery info
        DeliveryInfo deliveryInfo = DeliveryInfo.builder()
                .order(order)
                .address(request.getDeliveryInfo().getAddress())
                .city(request.getDeliveryInfo().getCity())
                .state(request.getDeliveryInfo().getState())
                .zipCode(request.getDeliveryInfo().getZipCode())
                .latitude(request.getDeliveryInfo().getLatitude() != null ? request.getDeliveryInfo().getLatitude().doubleValue() : null)
                .longitude(request.getDeliveryInfo().getLongitude() != null ? request.getDeliveryInfo().getLongitude().doubleValue() : null)
                .deliveryInstructions(request.getDeliveryInfo().getDeliveryInstructions())
                .build();
        order.setDeliveryInfo(deliveryInfo);

        // 7. Add payment info
        Payment payment = Payment.builder()
                .order(order)
                .method(PaymentMethod.valueOf(request.getPaymentMethod()))
                .status(PaymentStatus.PENDING)
                .amount(total)
                .build();
        order.setPayment(payment);

        // 8. Add initial status history
        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .order(order)
                .status(OrderStatus.NEW)
                .changedBy("CUSTOMER")
                .notes("Order placed")
                .build();
        order.addStatusHistory(statusHistory);

        // 9. Save order
        Order savedOrder = orderRepository.save(order);

        // 10. Send notifications
        notificationService.notifyNewOrder(savedOrder);

        // 11. Return response
        return mapToResponse(savedOrder);
    }

    public OrderResponse getOrderByNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));
        return mapToResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(String orderNumber, String reason) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderNumber));

        // Only allow cancellation if order is not being prepared yet
        if (order.getStatus() == OrderStatus.PREPARING ||
                order.getStatus() == OrderStatus.READY ||
                order.getStatus() == OrderStatus.ON_DELIVERY ||
                order.getStatus() == OrderStatus.DELIVERED) {
            throw new RuntimeException("Cannot cancel order in current status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);

        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .order(order)
                .status(OrderStatus.CANCELLED)
                .changedBy("CUSTOMER")
                .notes("Cancelled by customer: " + (reason != null ? reason : "No reason provided"))
                .build();
        order.addStatusHistory(statusHistory);

        Order savedOrder = orderRepository.save(order);

        // Notify about cancellation
        notificationService.notifyOrderCancelled(savedOrder);

        return mapToResponse(savedOrder);
    }

    private Customer findOrCreateCustomer(CreateOrderRequest.CustomerInfo customerInfo) {
        return customerRepository.findByPhone(customerInfo.getPhone())
                .orElseGet(() -> {
                    Customer newCustomer = Customer.builder()
                            .firstName(customerInfo.getFirstName())
                            .lastName(customerInfo.getLastName())
                            .phone(customerInfo.getPhone())
                            .email(customerInfo.getEmail())
                            .active(true)
                            .build();
                    return customerRepository.save(newCustomer);
                });
    }

    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private OrderResponse mapToResponse(Order order) {
        OrderResponse.RestaurantInfo restaurantInfo = OrderResponse.RestaurantInfo.builder()
                .id(order.getRestaurant().getId())
                .name(order.getRestaurant().getName())
                .phone(order.getRestaurant().getPhone())
                .address(order.getRestaurant().getAddress())
                .build();

        OrderResponse.CustomerInfo customerInfo = OrderResponse.CustomerInfo.builder()
                .id(order.getCustomer().getId())
                .firstName(order.getCustomer().getFirstName())
                .lastName(order.getCustomer().getLastName())
                .phone(order.getCustomer().getPhone())
                .email(order.getCustomer().getEmail())
                .build();

        List<OrderResponse.OrderItemInfo> itemsInfo = order.getItems().stream()
                .map(item -> OrderResponse.OrderItemInfo.builder()
                        .id(item.getId())
                        .productName(item.getProductName())
                        .quantity(item.getQuantity())
                        .price(item.getUnitPrice())
                        .total(item.getTotalPrice())
                        .specialInstructions(item.getSpecialInstructions())
                        .build())
                .toList();

        OrderResponse.DeliveryInfo deliveryInfo = null;
        if (order.getDeliveryInfo() != null) {
            deliveryInfo = OrderResponse.DeliveryInfo.builder()
                    .address(order.getDeliveryInfo().getAddress())
                    .city(order.getDeliveryInfo().getCity())
                    .state(order.getDeliveryInfo().getState())
                    .zipCode(order.getDeliveryInfo().getZipCode())
                    .latitude(order.getDeliveryInfo().getLatitude() != null ? BigDecimal.valueOf(order.getDeliveryInfo().getLatitude()) : null)
                    .longitude(order.getDeliveryInfo().getLongitude() != null ? BigDecimal.valueOf(order.getDeliveryInfo().getLongitude()) : null)
                    .deliveryInstructions(order.getDeliveryInfo().getDeliveryInstructions())
                    .courierName(order.getDeliveryInfo().getCourierName())
                    .courierPhone(order.getDeliveryInfo().getCourierPhone())
                    .build();
        }

        OrderResponse.PaymentInfo paymentInfo = null;
        if (order.getPayment() != null) {
            paymentInfo = OrderResponse.PaymentInfo.builder()
                    .paymentMethod(order.getPayment().getMethod().name())
                    .paymentStatus(order.getPayment().getStatus().name())
                    .amount(order.getPayment().getAmount())
                    .build();
        }

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .orderSource(order.getOrderSource())
                .subtotal(order.getSubtotal())
                .deliveryFee(order.getDeliveryFee())
                .tax(order.getTax())
                .discount(order.getDiscount())
                .total(order.getTotal())
                .customerNotes(order.getCustomerNotes())
                .scheduledFor(order.getScheduledFor())
                .createdAt(order.getCreatedAt())
                .estimatedDeliveryTime(order.getDeliveryInfo() != null ? order.getDeliveryInfo().getEstimatedDeliveryTime() : null)
                .restaurant(restaurantInfo)
                .customer(customerInfo)
                .items(itemsInfo)
                .deliveryInfo(deliveryInfo)
                .paymentInfo(paymentInfo)
                .build();
    }
}
