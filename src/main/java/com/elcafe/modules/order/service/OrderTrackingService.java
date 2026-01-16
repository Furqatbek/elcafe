package com.elcafe.modules.order.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.order.dto.OrderTrackingResponse;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.entity.OrderStatusHistory;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTrackingService {

    private final OrderRepository orderRepository;

    // Average preparation times in minutes by order type
    private static final int AVG_PREP_TIME_DINE_IN = 20;
    private static final int AVG_PREP_TIME_TAKEAWAY = 15;
    private static final int AVG_PREP_TIME_DELIVERY = 25;
    private static final int AVG_DELIVERY_TIME = 30;

    /**
     * Get full order tracking information
     */
    @Transactional(readOnly = true)
    public OrderTrackingResponse getOrderTracking(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));

        return buildTrackingResponse(order);
    }

    /**
     * Calculate ETA for an order
     */
    @Transactional(readOnly = true)
    public OrderTrackingResponse.ETAInfo calculateETA(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));

        return calculateETAForOrder(order);
    }

    /**
     * Get recent orders by phone number for tracking
     */
    @Transactional(readOnly = true)
    public List<OrderTrackingResponse> getRecentOrdersByPhone(String phone) {
        // Get orders from last 24 hours
        LocalDateTime since = LocalDateTime.now().minusHours(24);

        return orderRepository.findAll().stream()
                .filter(o -> o.getCustomer() != null &&
                        phone.equals(o.getCustomer().getPhone()) &&
                        o.getCreatedAt().isAfter(since))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .limit(5)
                .map(this::buildTrackingResponse)
                .collect(Collectors.toList());
    }

    private OrderTrackingResponse buildTrackingResponse(Order order) {
        // Build item summaries
        List<OrderTrackingResponse.OrderItemSummary> items = order.getItems().stream()
                .map(this::buildItemSummary)
                .collect(Collectors.toList());

        // Build status history
        List<OrderTrackingResponse.StatusUpdate> statusHistory = order.getStatusHistory().stream()
                .map(this::buildStatusUpdate)
                .collect(Collectors.toList());

        // Build delivery info if applicable
        OrderTrackingResponse.DeliveryInfo deliveryInfo = null;
        if (order.getOrderType() == OrderType.DELIVERY && order.getDeliveryInfo() != null) {
            deliveryInfo = buildDeliveryInfo(order);
        }

        return OrderTrackingResponse.builder()
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .orderType(order.getOrderType())
                .restaurantName(order.getRestaurant().getName())
                .restaurantPhone(order.getRestaurant().getPhone())
                .restaurantAddress(order.getRestaurant().getAddress())
                .totalAmount(order.getGrandTotal() != null ? order.getGrandTotal() : order.getTotal())
                .itemCount(order.getItems().size())
                .items(items)
                .createdAt(order.getCreatedAt())
                .confirmedAt(findStatusTimestamp(order, OrderStatus.CONFIRMED))
                .preparingAt(findStatusTimestamp(order, OrderStatus.PREPARING))
                .readyAt(findStatusTimestamp(order, OrderStatus.READY))
                .deliveredAt(findStatusTimestamp(order, OrderStatus.DELIVERED))
                .completedAt(findStatusTimestamp(order, OrderStatus.COMPLETED))
                .statusHistory(statusHistory)
                .eta(calculateETAForOrder(order))
                .deliveryInfo(deliveryInfo)
                .build();
    }

    private OrderTrackingResponse.OrderItemSummary buildItemSummary(OrderItem item) {
        return OrderTrackingResponse.OrderItemSummary.builder()
                .name(item.getProductName())
                .quantity(item.getQuantity())
                .price(item.getTotalPrice())
                .notes(item.getSpecialInstructions())
                .build();
    }

    private OrderTrackingResponse.StatusUpdate buildStatusUpdate(OrderStatusHistory history) {
        return OrderTrackingResponse.StatusUpdate.builder()
                .status(history.getStatus())
                .timestamp(history.getCreatedAt())
                .message(getStatusMessage(history.getStatus()))
                .build();
    }

    private OrderTrackingResponse.DeliveryInfo buildDeliveryInfo(Order order) {
        var deliveryInfo = order.getDeliveryInfo();

        return OrderTrackingResponse.DeliveryInfo.builder()
                .deliveryAddress(deliveryInfo.getAddress())
                .courierName(deliveryInfo.getCourierName())
                .courierPhone(deliveryInfo.getCourierPhone())
                .courierLatitude(deliveryInfo.getLatitude())
                .courierLongitude(deliveryInfo.getLongitude())
                .deliveryStatus(order.getStatus() != null ? order.getStatus().name() : null)
                .build();
    }

    private OrderTrackingResponse.ETAInfo calculateETAForOrder(Order order) {
        // If order is completed or cancelled, no ETA needed
        if (order.getStatus() == OrderStatus.COMPLETED ||
                order.getStatus() == OrderStatus.CANCELLED ||
                order.getStatus() == OrderStatus.DELIVERED) {
            return OrderTrackingResponse.ETAInfo.builder()
                    .etaMessage("Order " + order.getStatus().name().toLowerCase())
                    .minutesRemaining(0)
                    .isDelayed(false)
                    .build();
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime estimatedTime;
        int baseMinutes;

        // Calculate based on order type and current status
        switch (order.getOrderType()) {
            case DELIVERY:
                baseMinutes = AVG_PREP_TIME_DELIVERY + AVG_DELIVERY_TIME;
                break;
            case TAKEAWAY:
                baseMinutes = AVG_PREP_TIME_TAKEAWAY;
                break;
            default:
                baseMinutes = AVG_PREP_TIME_DINE_IN;
        }

        // Adjust based on current status
        LocalDateTime baseTime = order.getCreatedAt();
        if (order.getStatus() == OrderStatus.PREPARING) {
            // If preparing, use that timestamp
            LocalDateTime preparingAt = findStatusTimestamp(order, OrderStatus.PREPARING);
            if (preparingAt != null) {
                baseTime = preparingAt;
                baseMinutes = order.getOrderType() == OrderType.DELIVERY ?
                        AVG_PREP_TIME_DELIVERY / 2 + AVG_DELIVERY_TIME : baseMinutes / 2;
            }
        } else if (order.getStatus() == OrderStatus.READY) {
            // If ready, only delivery time remains
            LocalDateTime readyAt = findStatusTimestamp(order, OrderStatus.READY);
            if (readyAt != null) {
                baseTime = readyAt;
                baseMinutes = order.getOrderType() == OrderType.DELIVERY ? AVG_DELIVERY_TIME : 5;
            }
        } else if (order.getStatus() == OrderStatus.OUT_FOR_DELIVERY) {
            LocalDateTime outAt = findStatusTimestamp(order, OrderStatus.OUT_FOR_DELIVERY);
            if (outAt != null) {
                baseTime = outAt;
                baseMinutes = AVG_DELIVERY_TIME / 2;
            }
        }

        estimatedTime = baseTime.plusMinutes(baseMinutes);
        int minutesRemaining = (int) Math.max(0, ChronoUnit.MINUTES.between(now, estimatedTime));

        // Check if delayed
        boolean isDelayed = now.isAfter(estimatedTime);
        String delayReason = isDelayed ? "Higher than usual order volume" : null;

        String etaMessage;
        if (minutesRemaining <= 0 && !isDelayed) {
            etaMessage = "Any moment now";
        } else if (minutesRemaining <= 5) {
            etaMessage = "Less than 5 minutes";
        } else if (minutesRemaining <= 15) {
            etaMessage = "About 10-15 minutes";
        } else if (minutesRemaining <= 30) {
            etaMessage = "About 20-30 minutes";
        } else {
            etaMessage = "About " + minutesRemaining + " minutes";
        }

        return OrderTrackingResponse.ETAInfo.builder()
                .estimatedTime(estimatedTime)
                .minutesRemaining(minutesRemaining)
                .etaMessage(etaMessage)
                .isDelayed(isDelayed)
                .delayReason(delayReason)
                .build();
    }

    private LocalDateTime findStatusTimestamp(Order order, OrderStatus status) {
        return order.getStatusHistory().stream()
                .filter(h -> h.getStatus() == status)
                .map(OrderStatusHistory::getCreatedAt)
                .findFirst()
                .orElse(null);
    }

    private String getStatusMessage(OrderStatus status) {
        return switch (status) {
            case NEW -> "Order placed";
            case CONFIRMED -> "Order confirmed by restaurant";
            case PREPARING -> "Kitchen started preparing your order";
            case READY -> "Order is ready";
            case OUT_FOR_DELIVERY -> "Driver picked up your order";
            case DELIVERED -> "Order delivered";
            case COMPLETED -> "Order completed";
            case CANCELLED -> "Order cancelled";
            default -> status.name();
        };
    }
}
