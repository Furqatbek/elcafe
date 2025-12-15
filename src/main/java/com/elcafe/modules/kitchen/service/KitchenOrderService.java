package com.elcafe.modules.kitchen.service;

import com.elcafe.modules.kitchen.entity.KitchenOrder;
import com.elcafe.modules.kitchen.enums.KitchenOrderStatus;
import com.elcafe.modules.kitchen.enums.KitchenPriority;
import com.elcafe.modules.kitchen.repository.KitchenOrderRepository;
import com.elcafe.modules.notification.service.NotificationService;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderStatusHistory;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KitchenOrderService {

    private final KitchenOrderRepository kitchenOrderRepository;
    private final OrderRepository orderRepository;
    private final NotificationService notificationService;

    @Transactional
    public KitchenOrder createKitchenOrder(Order order) {
        KitchenOrder kitchenOrder = KitchenOrder.builder()
                .order(order)
                .status(KitchenOrderStatus.PENDING)
                .priority(KitchenPriority.NORMAL)
                .estimatedPreparationTimeMinutes(30) // Default 30 minutes
                .build();

        return kitchenOrderRepository.save(kitchenOrder);
    }

    public List<KitchenOrder> getActiveOrders(Long restaurantId) {
        List<KitchenOrderStatus> activeStatuses = Arrays.asList(
                KitchenOrderStatus.PENDING,
                KitchenOrderStatus.PREPARING
        );

        if (restaurantId != null) {
            return kitchenOrderRepository.findByRestaurantAndStatuses(restaurantId, activeStatuses);
        }
        return kitchenOrderRepository.findByStatusInOrderByPriorityDescCreatedAtAsc(activeStatuses);
    }

    public List<KitchenOrder> getReadyOrders(Long restaurantId) {
        List<KitchenOrderStatus> readyStatuses = List.of(KitchenOrderStatus.READY);

        if (restaurantId != null) {
            return kitchenOrderRepository.findByRestaurantAndStatuses(restaurantId, readyStatuses);
        }
        return kitchenOrderRepository.findByStatusOrderByCreatedAtAsc(KitchenOrderStatus.READY);
    }

    @Transactional
    public KitchenOrder startPreparation(Long kitchenOrderId, String chefName) {
        KitchenOrder kitchenOrder = kitchenOrderRepository.findById(kitchenOrderId)
                .orElseThrow(() -> new RuntimeException("Kitchen order not found"));

        if (kitchenOrder.getStatus() != KitchenOrderStatus.PENDING) {
            throw new RuntimeException("Order is not in PENDING status");
        }

        kitchenOrder.startPreparation(chefName);
        KitchenOrder savedOrder = kitchenOrderRepository.save(kitchenOrder);

        // Update main order status
        Order order = kitchenOrder.getOrder();
        order.setStatus(OrderStatus.PREPARING);

        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .order(order)
                .status(OrderStatus.PREPARING)
                .changedBy(chefName)
                .notes("Preparation started by " + chefName)
                .build();
        order.addStatusHistory(statusHistory);
        orderRepository.save(order);

        // Notify
        notificationService.notifyOrderPreparing(order);

        log.info("Kitchen order {} started preparation by {}", kitchenOrder.getId(), chefName);
        return savedOrder;
    }

    @Transactional
    public KitchenOrder markAsReady(Long kitchenOrderId) {
        KitchenOrder kitchenOrder = kitchenOrderRepository.findById(kitchenOrderId)
                .orElseThrow(() -> new RuntimeException("Kitchen order not found"));

        if (kitchenOrder.getStatus() != KitchenOrderStatus.PREPARING) {
            throw new RuntimeException("Order is not being prepared");
        }

        kitchenOrder.completePreparation();
        KitchenOrder savedOrder = kitchenOrderRepository.save(kitchenOrder);

        // Update main order status
        Order order = kitchenOrder.getOrder();
        order.setStatus(OrderStatus.READY);

        OrderStatusHistory statusHistory = OrderStatusHistory.builder()
                .order(order)
                .status(OrderStatus.READY)
                .changedBy("KITCHEN")
                .notes("Order ready for pickup/delivery")
                .build();
        order.addStatusHistory(statusHistory);
        orderRepository.save(order);

        // Notify couriers and customer
        notificationService.notifyOrderReady(order);

        log.info("Kitchen order {} marked as ready", kitchenOrder.getId());
        return savedOrder;
    }

    @Transactional
    public KitchenOrder markAsPickedUp(Long kitchenOrderId) {
        KitchenOrder kitchenOrder = kitchenOrderRepository.findById(kitchenOrderId)
                .orElseThrow(() -> new RuntimeException("Kitchen order not found"));

        kitchenOrder.setStatus(KitchenOrderStatus.PICKED_UP);
        return kitchenOrderRepository.save(kitchenOrder);
    }

    @Transactional
    public KitchenOrder updatePriority(Long kitchenOrderId, KitchenPriority priority) {
        KitchenOrder kitchenOrder = kitchenOrderRepository.findById(kitchenOrderId)
                .orElseThrow(() -> new RuntimeException("Kitchen order not found"));

        kitchenOrder.setPriority(priority);
        return kitchenOrderRepository.save(kitchenOrder);
    }
}
