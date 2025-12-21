package com.elcafe.modules.order.service;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.notification.service.NotificationService;
import com.elcafe.modules.order.dto.pos.CreatePOSOrderRequest;
import com.elcafe.modules.order.dto.pos.POSOrderResponse;
import com.elcafe.modules.order.entity.DeliveryInfo;
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
        order.setStatus(OrderStatus.PENDING);
        order.setCustomerNotes(request.getOrderNotes());

        // Set pricing (no tax)
        order.setSubtotal(request.getSubtotal());
        order.setTax(BigDecimal.ZERO);
        order.setDeliveryFee(request.getDeliveryFee() != null ? request.getDeliveryFee() : BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO);
        order.setTotal(request.getSubtotal().add(order.getDeliveryFee()));

        // Add order items
        List<OrderItem> orderItems = request.getItems().stream()
                .map(itemReq -> createOrderItem(itemReq, order))
                .collect(Collectors.toList());

        // Set items using the provided method
        for (OrderItem item : orderItems) {
            order.addItem(item);
        }

        // Handle delivery-specific info
        if (request.getOrderType() == CreatePOSOrderRequest.OrderType.DELIVERY) {
            if (request.getDeliveryInfo() == null) {
                throw new IllegalArgumentException("Delivery information is required for delivery orders");
            }
            DeliveryInfo deliveryInfo = createDeliveryInfo(request.getDeliveryInfo(), order);
            order.setDeliveryInfo(deliveryInfo);
        }

        // Note: Current Order entity doesn't support table number or guest count for dine-in
        // This would need to be added to the Order entity or handled separately

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

                    // Split name into first and last name
                    String[] nameParts = customerInfo.getName().trim().split("\\s+", 2);
                    String firstName = nameParts[0];
                    String lastName = nameParts.length > 1 ? nameParts[1] : "";

                    Customer newCustomer = new Customer();
                    newCustomer.setFirstName(firstName);
                    newCustomer.setLastName(lastName);
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
        orderItem.setProductId(product.getId());
        orderItem.setProductName(product.getName());
        orderItem.setQuantity(itemRequest.getQuantity());
        orderItem.setUnitPrice(itemRequest.getPrice());
        orderItem.setTotalPrice(itemRequest.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity())));
        orderItem.setSpecialInstructions(itemRequest.getNotes());

        // Add modifiers as add-ons JSON or text if present
        if (itemRequest.getModifiers() != null && !itemRequest.getModifiers().isEmpty()) {
            String modifiers = itemRequest.getModifiers().stream()
                    .map(m -> m.getName() + (m.getPrice().compareTo(BigDecimal.ZERO) > 0 ? " (+$" + m.getPrice() + ")" : ""))
                    .collect(Collectors.joining(", "));
            orderItem.setAddOns(modifiers);
        }

        return orderItem;
    }

    private DeliveryInfo createDeliveryInfo(CreatePOSOrderRequest.DeliveryInfo deliveryInfoReq, Order order) {
        DeliveryInfo deliveryInfo = new DeliveryInfo();
        deliveryInfo.setOrder(order);
        deliveryInfo.setAddress(deliveryInfoReq.getStreet());
        deliveryInfo.setCity(deliveryInfoReq.getCity());
        deliveryInfo.setState(deliveryInfoReq.getState());
        deliveryInfo.setZipCode(deliveryInfoReq.getZipCode());
        deliveryInfo.setDeliveryInstructions(deliveryInfoReq.getDeliveryInstructions());
        deliveryInfo.setEstimatedDeliveryTime(LocalDateTime.now().plusMinutes(45));
        return deliveryInfo;
    }

    private POSOrderResponse mapToResponse(Order order, String orderType) {
        POSOrderResponse response = POSOrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .orderType(orderType)
                .customerName(order.getCustomer().getFirstName() + " " + order.getCustomer().getLastName())
                .customerPhone(order.getCustomer().getPhone())
                .subtotal(order.getSubtotal())
                .deliveryFee(order.getDeliveryFee())
                .total(order.getTotal())
                .orderNotes(order.getCustomerNotes())
                .createdAt(order.getCreatedAt())
                .build();

        // Map order items
        List<POSOrderResponse.OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> {
                    POSOrderResponse.OrderItemResponse itemResponse = new POSOrderResponse.OrderItemResponse();
                    itemResponse.setId(item.getId());
                    itemResponse.setProductName(item.getProductName());
                    itemResponse.setQuantity(item.getQuantity());
                    itemResponse.setPrice(item.getUnitPrice());
                    itemResponse.setNotes(item.getSpecialInstructions());

                    // Parse modifiers from addOns field
                    if (item.getAddOns() != null && !item.getAddOns().isEmpty()) {
                        List<String> modifiers = List.of(item.getAddOns().split(", "));
                        itemResponse.setModifiers(modifiers);
                    }

                    return itemResponse;
                })
                .collect(Collectors.toList());
        response.setItems(itemResponses);

        // Add delivery address if applicable
        if ("DELIVERY".equals(orderType) && order.getDeliveryInfo() != null) {
            DeliveryInfo deliveryInfo = order.getDeliveryInfo();
            POSOrderResponse.DeliveryAddressResponse deliveryAddress = POSOrderResponse.DeliveryAddressResponse.builder()
                    .street(deliveryInfo.getAddress())
                    .city(deliveryInfo.getCity())
                    .state(deliveryInfo.getState())
                    .zipCode(deliveryInfo.getZipCode())
                    .deliveryInstructions(deliveryInfo.getDeliveryInstructions())
                    .build();
            response.setDeliveryAddress(deliveryAddress);
            response.setEstimatedDeliveryTime(deliveryInfo.getEstimatedDeliveryTime());
        }

        // Note: Dine-in info not supported in current Order entity structure
        // Would need to add tableNumber and guestCount fields to Order entity

        return response;
    }
}
