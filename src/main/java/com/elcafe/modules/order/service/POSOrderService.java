package com.elcafe.modules.order.service;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.notification.service.NotificationService;
import com.elcafe.modules.order.dto.pos.CreatePOSOrderRequest;
import com.elcafe.modules.order.dto.pos.POSOrderResponse;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class POSOrderService {

    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;

    @Transactional
    public POSOrderResponse createOrder(CreatePOSOrderRequest request) {
        log.info("Creating POS order: type={}, restaurant={}", request.getOrderType(), request.getRestaurantId());

        // Validate restaurant
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found with ID: " + request.getRestaurantId()));

        // Find or create customer
        Customer customer = findOrCreateCustomer(request.getCustomerInfo());

        // Create order
        Order order = new Order();
        order.setRestaurant(restaurant);
        order.setCustomer(customer);
        order.setOrderSource(request.getOrderSource());
        order.setStatus(OrderStatus.PENDING);
        order.setCustomerNotes(request.getOrderNotes());
        order.setPaymentMethod(request.getPaymentMethod());

        // Set order type specific fields
        switch (request.getOrderType()) {
            case DELIVERY:
                if (request.getDeliveryInfo() == null) {
                    throw new IllegalArgumentException("Delivery information is required for delivery orders");
                }
                order.setDeliveryAddress(formatDeliveryAddress(request.getDeliveryInfo()));
                order.setDeliveryInstructions(request.getDeliveryInfo().getDeliveryInstructions());
                order.setEstimatedDeliveryTime(LocalDateTime.now().plusMinutes(45));
                break;
            case DINE_IN:
                if (request.getDineInInfo() == null) {
                    throw new IllegalArgumentException("Dine-in information is required for dine-in orders");
                }
                order.setTableNumber(request.getDineInInfo().getTableNumber());
                order.setGuestCount(request.getDineInInfo().getGuestCount());
                order.setEstimatedDeliveryTime(LocalDateTime.now().plusMinutes(20));
                break;
            case TAKEAWAY:
                order.setEstimatedDeliveryTime(LocalDateTime.now().plusMinutes(15));
                break;
        }

        // Add order items
        List<OrderItem> orderItems = request.getItems().stream()
                .map(itemReq -> createOrderItem(itemReq, order))
                .collect(Collectors.toList());
        order.setItems(orderItems);

        // Set pricing
        order.setSubtotal(request.getSubtotal());
        order.setTax(request.getTax());
        order.setDeliveryFee(request.getDeliveryFee() != null ? request.getDeliveryFee() : BigDecimal.ZERO);
        order.setTotal(request.getTotal());

        // Save order
        Order savedOrder = orderRepository.save(order);

        // Force initialize lazy relationships
        savedOrder.getRestaurant().getName();
        savedOrder.getCustomer().getPhone();
        savedOrder.getItems().size();
        savedOrder.getStatusHistory().size();

        log.info("POS order created successfully: {}", savedOrder.getOrderNumber());

        // Send notifications
        try {
            notificationService.notifyNewOrder(savedOrder);
        } catch (Exception e) {
            log.error("Failed to send notifications for order {}", savedOrder.getOrderNumber(), e);
        }

        return mapToResponse(savedOrder, request.getOrderType().name());
    }

    private Customer findOrCreateCustomer(CreatePOSOrderRequest.CustomerInfo customerInfo) {
        // Try to find existing customer by phone
        return customerRepository.findByPhone(customerInfo.getPhone())
                .orElseGet(() -> {
                    log.info("Creating new customer with phone: {}", customerInfo.getPhone());
                    Customer newCustomer = new Customer();
                    newCustomer.setName(customerInfo.getName());
                    newCustomer.setPhone(customerInfo.getPhone());
                    newCustomer.setEmail(customerInfo.getEmail());
                    return customerRepository.save(newCustomer);
                });
    }

    private OrderItem createOrderItem(CreatePOSOrderRequest.OrderItemRequest itemRequest, Order order) {
        Product product = productRepository.findById(itemRequest.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + itemRequest.getProductId()));

        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setProduct(product);
        orderItem.setQuantity(itemRequest.getQuantity());
        orderItem.setPrice(itemRequest.getPrice());
        orderItem.setSpecialInstructions(itemRequest.getNotes());

        // Add modifiers as special instructions if present
        if (itemRequest.getModifiers() != null && !itemRequest.getModifiers().isEmpty()) {
            String modifiers = itemRequest.getModifiers().stream()
                    .map(m -> m.getName() + (m.getPrice().compareTo(BigDecimal.ZERO) > 0 ? " (+$" + m.getPrice() + ")" : ""))
                    .collect(Collectors.joining(", "));

            String instructions = orderItem.getSpecialInstructions();
            orderItem.setSpecialInstructions(
                    instructions != null && !instructions.isEmpty()
                            ? "Modifiers: " + modifiers + "; " + instructions
                            : "Modifiers: " + modifiers
            );
        }

        return orderItem;
    }

    private String formatDeliveryAddress(CreatePOSOrderRequest.DeliveryInfo deliveryInfo) {
        StringBuilder address = new StringBuilder(deliveryInfo.getStreet());
        address.append(", ").append(deliveryInfo.getCity());

        if (deliveryInfo.getState() != null && !deliveryInfo.getState().isEmpty()) {
            address.append(", ").append(deliveryInfo.getState());
        }

        if (deliveryInfo.getZipCode() != null && !deliveryInfo.getZipCode().isEmpty()) {
            address.append(" ").append(deliveryInfo.getZipCode());
        }

        return address.toString();
    }

    private POSOrderResponse mapToResponse(Order order, String orderType) {
        POSOrderResponse response = POSOrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .orderType(orderType)
                .customerName(order.getCustomer().getName())
                .customerPhone(order.getCustomer().getPhone())
                .subtotal(order.getSubtotal())
                .tax(order.getTax())
                .deliveryFee(order.getDeliveryFee())
                .total(order.getTotal())
                .paymentMethod(order.getPaymentMethod())
                .orderNotes(order.getCustomerNotes())
                .createdAt(order.getCreatedAt())
                .estimatedDeliveryTime(order.getEstimatedDeliveryTime())
                .build();

        // Map order items
        List<POSOrderResponse.OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> {
                    POSOrderResponse.OrderItemResponse itemResponse = new POSOrderResponse.OrderItemResponse();
                    itemResponse.setId(item.getId());
                    itemResponse.setProductName(item.getProduct().getName());
                    itemResponse.setQuantity(item.getQuantity());
                    itemResponse.setPrice(item.getPrice());
                    itemResponse.setNotes(item.getSpecialInstructions());
                    return itemResponse;
                })
                .collect(Collectors.toList());
        response.setItems(itemResponses);

        // Add delivery address if applicable
        if ("DELIVERY".equals(orderType) && order.getDeliveryAddress() != null) {
            String[] addressParts = order.getDeliveryAddress().split(", ");
            POSOrderResponse.DeliveryAddressResponse deliveryAddress = POSOrderResponse.DeliveryAddressResponse.builder()
                    .street(addressParts.length > 0 ? addressParts[0] : "")
                    .city(addressParts.length > 1 ? addressParts[1] : "")
                    .deliveryInstructions(order.getDeliveryInstructions())
                    .build();
            response.setDeliveryAddress(deliveryAddress);
        }

        // Add dine-in info if applicable
        if ("DINE_IN".equals(orderType) && order.getTableNumber() != null) {
            POSOrderResponse.DineInInfoResponse dineInInfo = POSOrderResponse.DineInInfoResponse.builder()
                    .tableNumber(order.getTableNumber())
                    .guestCount(order.getGuestCount())
                    .build();
            response.setDineInInfo(dineInInfo);
        }

        return response;
    }
}
