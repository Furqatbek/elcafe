package com.elcafe.modules.order.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.enums.RegistrationSource;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.dto.consumer.CreateOrderRequest;
import com.elcafe.modules.order.entity.*;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final RestaurantRepository restaurantRepository;
    private final ProductRepository productRepository;

    @Transactional
    public Order createOrder(Order order) {
        log.info("Creating new order");

        order.setOrderNumber(generateOrderNumber());
        order.setStatus(OrderStatus.NEW);

        OrderStatusHistory history = OrderStatusHistory.builder()
                .status(OrderStatus.NEW)
                .changedBy("SYSTEM")
                .notes("Order created")
                .build();
        order.addStatusHistory(history);

        order = orderRepository.save(order);
        log.info("Order created with number: {}", order.getOrderNumber());

        return order;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        log.info("Creating new order from request");

        // 1. Lookup or create customer
        Customer customer = findOrCreateCustomer(request.getCustomerInfo());

        // 2. Get restaurant
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", request.getRestaurantId()));

        // 3. Create order items and calculate totals
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (CreateOrderRequest.OrderItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", itemRequest.getProductId()));

            BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity()));
            subtotal = subtotal.add(itemTotal);

            OrderItem orderItem = OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(product.getPrice())
                    .totalPrice(itemTotal)
                    .specialInstructions(itemRequest.getSpecialInstructions())
                    .build();

            orderItems.add(orderItem);
        }

        // 4. Calculate fees and total
        BigDecimal deliveryFee = BigDecimal.valueOf(5.00); // Default delivery fee
        BigDecimal tax = subtotal.multiply(BigDecimal.valueOf(0.08)); // 8% tax
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal total = subtotal.add(deliveryFee).add(tax).subtract(discount);

        // 5. Create delivery info
        DeliveryInfo deliveryInfo = DeliveryInfo.builder()
                .address(request.getDeliveryInfo().getAddress())
                .city(request.getDeliveryInfo().getCity())
                .state(request.getDeliveryInfo().getState())
                .zipCode(request.getDeliveryInfo().getZipCode())
                .latitude(request.getDeliveryInfo().getLatitude() != null ?
                         request.getDeliveryInfo().getLatitude().doubleValue() : null)
                .longitude(request.getDeliveryInfo().getLongitude() != null ?
                          request.getDeliveryInfo().getLongitude().doubleValue() : null)
                .deliveryInstructions(request.getDeliveryInfo().getDeliveryInstructions())
                .contactPhone(request.getCustomerInfo().getPhone())
                .contactName(request.getCustomerInfo().getFirstName() + " " + request.getCustomerInfo().getLastName())
                .build();

        // 6. Create payment
        Payment payment = Payment.builder()
                .method(PaymentMethod.valueOf(request.getPaymentMethod()))
                .status(PaymentStatus.PENDING)
                .amount(total)
                .build();

        // 7. Create order
        Order order = Order.builder()
                .restaurant(restaurant)
                .customer(customer)
                .orderSource(request.getOrderSource() != null ? request.getOrderSource() : OrderSource.ADMIN_PANEL)
                .subtotal(subtotal)
                .deliveryFee(deliveryFee)
                .tax(tax)
                .discount(discount)
                .total(total)
                .customerNotes(request.getCustomerNotes())
                .scheduledFor(request.getScheduledFor())
                .items(new ArrayList<>())
                .build();

        // Set order number and status
        order.setOrderNumber(generateOrderNumber());
        order.setStatus(OrderStatus.NEW);

        // Add status history
        OrderStatusHistory history = OrderStatusHistory.builder()
                .status(OrderStatus.NEW)
                .changedBy("SYSTEM")
                .notes("Order created")
                .build();
        order.addStatusHistory(history);

        // Set relationships properly using helper methods
        for (OrderItem item : orderItems) {
            order.addItem(item);
        }
        order.setDeliveryInfo(deliveryInfo);
        order.setPayment(payment);

        // Save order (cascade will save items, delivery info, payment, and history)
        order = orderRepository.save(order);
        log.info("Order created with number: {}", order.getOrderNumber());

        return order;
    }

    private Customer findOrCreateCustomer(CreateOrderRequest.CustomerInfo customerInfo) {
        // Try to find existing customer by phone
        return customerRepository.findByPhone(customerInfo.getPhone())
                .orElseGet(() -> {
                    log.info("Creating new customer with phone: {}", customerInfo.getPhone());
                    Customer newCustomer = Customer.builder()
                            .firstName(customerInfo.getFirstName())
                            .lastName(customerInfo.getLastName())
                            .phone(customerInfo.getPhone())
                            .email(customerInfo.getEmail())
                            .active(true)
                            .registrationSource(RegistrationSource.ADMIN_PANEL)
                            .build();
                    return customerRepository.save(newCustomer);
                });
    }

    @Transactional
    public Order updateOrderStatus(Long orderId, OrderStatus newStatus, String notes, String changedBy) {
        log.info("Updating order {} to status: {}", orderId, newStatus);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

        OrderStatus currentStatus = order.getStatus();
        if (!isValidStatusTransition(currentStatus, newStatus)) {
            throw new BadRequestException(
                    String.format("Invalid status transition from %s to %s", currentStatus, newStatus)
            );
        }

        order.setStatus(newStatus);

        OrderStatusHistory history = OrderStatusHistory.builder()
                .status(newStatus)
                .changedBy(changedBy)
                .notes(notes)
                .build();
        order.addStatusHistory(history);

        order = orderRepository.save(order);
        log.info("Order status updated: {} -> {}", currentStatus, newStatus);

        return order;
    }

    @Transactional(readOnly = true)
    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
    }

    @Transactional(readOnly = true)
    public Order getOrderByNumber(String orderNumber) {
        return orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));
    }

    @Transactional(readOnly = true)
    public Page<Order> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByRestaurant(Long restaurantId) {
        return orderRepository.findByRestaurantIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                restaurantId,
                LocalDateTime.now().minusDays(7),
                LocalDateTime.now()
        );
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByCustomer(Long customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @Transactional(readOnly = true)
    public List<Order> getPendingOrders() {
        return orderRepository.findByStatusOrderByCreatedAtAsc(OrderStatus.NEW);
    }

    private String generateOrderNumber() {
        return "ORD-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private boolean isValidStatusTransition(OrderStatus current, OrderStatus next) {
        return switch (current) {
            case NEW -> next == OrderStatus.ACCEPTED || next == OrderStatus.CANCELLED;
            case ACCEPTED -> next == OrderStatus.PREPARING || next == OrderStatus.CANCELLED;
            case PREPARING -> next == OrderStatus.READY || next == OrderStatus.CANCELLED;
            case READY -> next == OrderStatus.COURIER_ASSIGNED || next == OrderStatus.CANCELLED;
            case COURIER_ASSIGNED -> next == OrderStatus.ON_DELIVERY || next == OrderStatus.CANCELLED;
            case ON_DELIVERY -> next == OrderStatus.DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }
}
