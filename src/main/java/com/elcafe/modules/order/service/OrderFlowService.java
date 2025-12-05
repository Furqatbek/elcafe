package com.elcafe.modules.order.service;

import com.elcafe.modules.kitchen.entity.KitchenOrder;
import com.elcafe.modules.kitchen.service.KitchenOrderService;
import com.elcafe.modules.notification.service.NotificationService;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderStatusHistory;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service to orchestrate the complete order flow from placement to delivery
 * Flow: NEW -> ACCEPTED -> PREPARING -> READY -> COURIER_ASSIGNED -> ON_DELIVERY -> DELIVERED
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderFlowService {

    private final OrderRepository orderRepository;
    private final KitchenOrderService kitchenOrderService;
    private final NotificationService notificationService;

    /**
     * Accept order and send to kitchen
     * Status: NEW -> ACCEPTED
     */
    @Transactional
    public Order acceptOrder(Long orderId, String acceptedBy) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (order.getStatus() != OrderStatus.NEW) {
            throw new RuntimeException("Order cannot be accepted in current status: " + order.getStatus());
        }

        // Update order status
        order.setStatus(OrderStatus.ACCEPTED);

        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .order(order)
                .status(OrderStatus.ACCEPTED)
                .changedBy(acceptedBy)
                .notes("Order accepted by restaurant")
                .build();
        order.addStatusHistory(statusHistory);

        Order savedOrder = orderRepository.save(order);

        // Create kitchen order
        KitchenOrder kitchenOrder = kitchenOrderService.createKitchenOrder(savedOrder);
        log.info("Created kitchen order {} for order {}", kitchenOrder.getId(), order.getOrderNumber());

        // Notify
        notificationService.notifyOrderAccepted(savedOrder);

        log.info("Order {} accepted and sent to kitchen", order.getOrderNumber());
        return savedOrder;
    }

    /**
     * Complete order lifecycle summary
     */
    public String getOrderFlowDocumentation() {
        return """

                ╔═══════════════════════════════════════════════════════════════════════════╗
                ║                    COMPLETE ORDER FLOW SYSTEM                             ║
                ╚═══════════════════════════════════════════════════════════════════════════╝

                📱 1. CUSTOMER PLACES ORDER (Consumer Order API)
                   └─► POST /api/v1/consumer/orders
                       • Customer selects items from menu
                       • Provides delivery information
                       • Order created with status: NEW
                       • Notifications sent to restaurant & kitchen

                ✅ 2. RESTAURANT ACCEPTS ORDER
                   └─► POST /api/v1/orders/{id}/accept
                       • Restaurant confirms order
                       • Status: NEW → ACCEPTED
                       • Kitchen order created automatically
                       • Customer notified

                👨‍🍳 3. KITCHEN PREPARES FOOD (Kitchen Module)
                   └─► POST /api/v1/kitchen/orders/{id}/start
                       • Chef starts preparation
                       • Status: ACCEPTED → PREPARING
                       • Customer notified

                   └─► POST /api/v1/kitchen/orders/{id}/ready
                       • Food ready for pickup
                       • Status: PREPARING → READY
                       • Couriers notified

                🚗 4. COURIER ACCEPTS DELIVERY
                   └─► GET /api/v1/courier/orders/available
                       • Couriers see available orders

                   └─► POST /api/v1/courier/orders/{id}/accept
                       • Courier accepts order
                       • Status: READY → COURIER_ASSIGNED
                       • Customer notified with courier details

                   └─► POST /api/v1/courier/orders/{id}/decline
                       • Courier declines order
                       • System finds another courier

                🚚 5. COURIER STARTS DELIVERY
                   └─► POST /api/v1/courier/orders/{id}/start-delivery
                       • Courier picks up order
                       • Status: COURIER_ASSIGNED → ON_DELIVERY
                       • Customer gets real-time updates

                ✅ 6. DELIVERY COMPLETED
                   └─► POST /api/v1/courier/orders/{id}/complete
                       • Courier delivers order
                       • Status: ON_DELIVERY → DELIVERED
                       • Customer & restaurant notified

                ❌ CANCELLATION FLOW
                   └─► POST /api/v1/consumer/orders/{orderNumber}/cancel
                       • Customer can cancel before PREPARING
                       • Status: ANY → CANCELLED
                       • All parties notified

                ╔═══════════════════════════════════════════════════════════════════════════╗
                ║                      NOTIFICATION SYSTEM                                  ║
                ╚═══════════════════════════════════════════════════════════════════════════╝

                Each status change triggers notifications to:
                • 📱 Customer (SMS, Email, Push)
                • 🍴 Restaurant/Kitchen Staff (WebSocket, Dashboard)
                • 🚗 Couriers (Mobile App, Push)
                • 👤 Admin Panel (Real-time Dashboard)

                ╔═══════════════════════════════════════════════════════════════════════════╗
                ║                      ORDER TRACKING                                       ║
                ╚═══════════════════════════════════════════════════════════════════════════╝

                • GET /api/v1/consumer/orders/{orderNumber}
                  → Track order status in real-time
                  → See courier location (when assigned)
                  → Estimated delivery time
                  → Full order history

                """;
    }
}
