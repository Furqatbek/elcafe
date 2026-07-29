package com.elcafe.modules.order.service;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.notification.service.NotificationService;
import com.elcafe.modules.order.dto.consumer.CreateOrderRequest;
import com.elcafe.modules.order.dto.consumer.OrderResponse;
import com.elcafe.modules.order.entity.*;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.promotion.dto.ApplyDiscountRequest;
import com.elcafe.modules.promotion.dto.ValidateCouponRequest;
import com.elcafe.modules.promotion.dto.ValidateCouponResponse;
import com.elcafe.modules.promotion.enums.DiscountType;
import com.elcafe.modules.promotion.service.CouponValidationService;
import com.elcafe.modules.promotion.service.DiscountCalculationService;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
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
    private final CouponValidationService couponValidationService;
    private final DiscountCalculationService discountCalculationService;
    // @Lazy: the central status machine reaches back across the order module; keeping this edge lazy
    // avoids a startup cycle. Used only to auto-accept Telegram orders so they reach the KDS.
    @Lazy
    private final OrderService orderService;

    /**
     * Telegram Mini App orders auto-accept by default so they land on the kitchen display immediately.
     * Set false to hold them at NEW for staff to review first, like website/mobile orders.
     */
    @Value("${app.telegram.miniapp.auto-accept:true}")
    private boolean telegramAutoAccept;

    @Transactional
    public OrderResponse placeOrder(CreateOrderRequest request) {
        // 1. Validate restaurant
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant not found"));

        if (!restaurant.getActive()) {
            throw new BadRequestException("Restaurant is not active");
        }

        if (!restaurant.getAcceptingOrders()) {
            throw new BadRequestException("Restaurant is not accepting orders");
        }

        // 2. Find or create customer (optional)
        Customer customer = request.getCustomerInfo() != null ? findOrCreateCustomer(request.getCustomerInfo(), restaurant.getId()) : null;

        // 3. Build order
        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .restaurant(restaurant)
                .customer(customer)
                .status(OrderStatus.NEW)
                .orderType(request.getOrderType())
                .orderSource(request.getOrderSource())
                .customerNotes(request.getCustomerNotes())
                .scheduledFor(request.getScheduledFor() != null ? request.getScheduledFor().atOffset(ZoneOffset.UTC) : null)
                .items(new ArrayList<>())
                .statusHistory(new ArrayList<>())
                .build();

        // 4. Add order items
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CreateOrderRequest.OrderItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + itemRequest.getProductId()));

            if (!product.getInStock()) {
                throw new BadRequestException("Product not available: " + product.getName());
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

        // 5. Calculate costs. Pickup/dine-in never carry a delivery fee; a null orderType keeps the
        // legacy behavior (fee applied) so existing website/mobile callers are unaffected.
        boolean chargeDeliveryFee = request.getOrderType() != OrderType.TAKEAWAY
                && request.getOrderType() != OrderType.DINE_IN;
        BigDecimal deliveryFee = chargeDeliveryFee && restaurant.getDeliveryFee() != null
                ? restaurant.getDeliveryFee() : BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO; // No tax

        order.setSubtotal(subtotal);
        order.setDeliveryFee(deliveryFee);
        order.setTax(tax);
        order.setDiscount(BigDecimal.ZERO);

        // 5.1 Apply coupon if provided
        if (request.getCouponCode() != null && !request.getCouponCode().isBlank()) {
            try {
                // Build validation request
                ValidateCouponRequest validateRequest = ValidateCouponRequest.builder()
                        .code(request.getCouponCode())
                        .restaurantId(restaurant.getId())
                        .customerId(customer != null ? customer.getId() : null)
                        .orderSubtotal(subtotal)
                        .items(request.getItems().stream()
                                .map(item -> {
                                    Product product = productRepository.findById(item.getProductId()).orElse(null);
                                    return ValidateCouponRequest.OrderItemInfo.builder()
                                            .productId(item.getProductId())
                                            .quantity(item.getQuantity())
                                            .price(product != null ? product.getPrice() : BigDecimal.ZERO)
                                            .build();
                                })
                                .toList())
                        .build();

                ValidateCouponResponse couponResponse = couponValidationService.validateCoupon(validateRequest);

                if (couponResponse.getValid()) {
                    // Apply the discount
                    ApplyDiscountRequest discountRequest = ApplyDiscountRequest.builder()
                            .couponCode(request.getCouponCode())
                            .discountType(DiscountType.COUPON)
                            .build();
                    discountCalculationService.applyDiscount(order, discountRequest);
                    log.info("Coupon {} applied to consumer order: discount={}", request.getCouponCode(), order.getDiscount());
                } else {
                    log.warn("Invalid coupon code {} for consumer order: {}", request.getCouponCode(), couponResponse.getErrorMessage());
                }
            } catch (Exception e) {
                log.warn("Failed to apply coupon {} for consumer order: {}", request.getCouponCode(), e.getMessage());
                // Continue without discount - don't fail the order
            }
        }

        // Calculate total
        BigDecimal total = subtotal.add(deliveryFee).add(tax).subtract(order.getDiscount());
        order.setTotal(total);

        // 6. Add delivery info (optional)
        if (request.getDeliveryInfo() != null) {
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
        }

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

        // 11. Telegram Mini App orders auto-accept by default so they reach the KDS immediately — the
        // NEW→ACCEPTED transition creates the kitchen ticket (OrderService). A restaurant can turn this
        // off to review Telegram orders first, in which case they sit at NEW for manual acceptance like
        // website/mobile orders. Best-effort: a failed auto-accept must not fail the placed order.
        if (telegramAutoAccept && request.getOrderSource() == OrderSource.TELEGRAM_BOT) {
            try {
                savedOrder = orderService.updateOrderStatus(savedOrder.getId(), OrderStatus.ACCEPTED,
                        "Auto-accepted (Telegram order)", "SYSTEM");
            } catch (Exception e) {
                log.error("Auto-accept failed for Telegram order {} — it stays NEW for manual accept",
                        savedOrder.getOrderNumber(), e);
            }
        }

        // 12. Return response
        return mapToResponse(savedOrder);
    }

    /**
     * §3.7: order numbers are sequential and guessable, so a consumer may only track or cancel an
     * order that belongs to them. These endpoints are authenticated, so requesterCustomerId is the
     * caller's CustomerPrincipal id; a null id or a non-owning order is rejected.
     */
    private void assertOwnedBy(Order order, Long requesterCustomerId) {
        if (requesterCustomerId == null || order.getCustomer() == null
                || !order.getCustomer().getId().equals(requesterCustomerId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "This order does not belong to you");
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumber(String orderNumber, Long requesterCustomerId) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNumber));
        assertOwnedBy(order, requesterCustomerId);

        // Force initialization of lazy relationships
        order.getRestaurant().getName();
        if (order.getCustomer() != null) {
            order.getCustomer().getPhone();
        }
        order.getItems().size();
        if (order.getDeliveryInfo() != null) {
            order.getDeliveryInfo().getAddress();
        }

        return mapToResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(String orderNumber, String reason, Long requesterCustomerId) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderNumber));
        assertOwnedBy(order, requesterCustomerId);

        // Only allow cancellation if order is not being prepared yet
        if (order.getStatus() == OrderStatus.PREPARING ||
                order.getStatus() == OrderStatus.READY ||
                order.getStatus() == OrderStatus.ON_DELIVERY ||
                order.getStatus() == OrderStatus.DELIVERED) {
            throw new BadRequestException("Cannot cancel order in current status: " + order.getStatus());
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

    private Customer findOrCreateCustomer(CreateOrderRequest.CustomerInfo customerInfo, Long restaurantId) {
        // If phone is provided, try to find existing customer for THIS restaurant (V150).
        if (customerInfo.getPhone() != null && !customerInfo.getPhone().isBlank()) {
            return customerRepository.findByPhoneAndRestaurantId(customerInfo.getPhone(), restaurantId)
                    .orElseGet(() -> {
                        Customer newCustomer = Customer.builder()
                                .restaurantId(restaurantId)
                                .firstName(customerInfo.getFirstName())
                                .lastName(customerInfo.getLastName())
                                .phone(customerInfo.getPhone())
                                .email(customerInfo.getEmail())
                                .active(true)
                                .build();
                        return customerRepository.save(newCustomer);
                    });
        }

        // No phone - create new customer without lookup
        Customer newCustomer = Customer.builder()
                .restaurantId(restaurantId)
                .firstName(customerInfo.getFirstName())
                .lastName(customerInfo.getLastName())
                .phone(customerInfo.getPhone())
                .email(customerInfo.getEmail())
                .active(true)
                .build();
        return customerRepository.save(newCustomer);
    }

    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Validate a coupon code before checkout
     */
    public ValidateCouponResponse validateCoupon(Long restaurantId, String couponCode, BigDecimal orderTotal,
                                                  Long customerId, List<CreateOrderRequest.OrderItemRequest> items) {
        log.info("Validating coupon {} for restaurant {}", couponCode, restaurantId);

        ValidateCouponRequest validateRequest = ValidateCouponRequest.builder()
                .code(couponCode)
                .restaurantId(restaurantId)
                .customerId(customerId)
                .orderSubtotal(orderTotal)
                .items(items != null ? items.stream()
                        .map(item -> {
                            Product product = productRepository.findById(item.getProductId()).orElse(null);
                            return ValidateCouponRequest.OrderItemInfo.builder()
                                    .productId(item.getProductId())
                                    .quantity(item.getQuantity())
                                    .price(product != null ? product.getPrice() : BigDecimal.ZERO)
                                    .build();
                        })
                        .toList() : List.of())
                .build();

        return couponValidationService.validateCoupon(validateRequest);
    }

    private OrderResponse mapToResponse(Order order) {
        OrderResponse.RestaurantInfo restaurantInfo = OrderResponse.RestaurantInfo.builder()
                .id(order.getRestaurant().getId())
                .name(order.getRestaurant().getName())
                .phone(order.getRestaurant().getPhone())
                .address(order.getRestaurant().getAddress())
                .build();

        OrderResponse.CustomerInfo customerInfo = null;
        if (order.getCustomer() != null) {
            customerInfo = OrderResponse.CustomerInfo.builder()
                    .id(order.getCustomer().getId())
                    .firstName(order.getCustomer().getFirstName())
                    .lastName(order.getCustomer().getLastName())
                    .phone(order.getCustomer().getPhone())
                    .email(order.getCustomer().getEmail())
                    .build();
        }

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
                .trackingToken(order.getTrackingToken()) // returned to the ordering customer so they can build a tracking link (audit #17)
                .status(order.getStatus())
                .orderSource(order.getOrderSource())
                .subtotal(order.getSubtotal())
                .deliveryFee(order.getDeliveryFee())
                .tax(order.getTax())
                .discount(order.getDiscount())
                .total(order.getTotal())
                .customerNotes(order.getCustomerNotes())
                .scheduledFor(order.getScheduledFor() != null ? order.getScheduledFor().toLocalDateTime() : null)
                .createdAt(order.getCreatedAt().toLocalDateTime())
                .estimatedDeliveryTime(order.getDeliveryInfo() != null && order.getDeliveryInfo().getEstimatedDeliveryTime() != null ? order.getDeliveryInfo().getEstimatedDeliveryTime().toLocalDateTime() : null)
                .restaurant(restaurantInfo)
                .customer(customerInfo)
                .items(itemsInfo)
                .deliveryInfo(deliveryInfo)
                .paymentInfo(paymentInfo)
                .build();
    }
}
