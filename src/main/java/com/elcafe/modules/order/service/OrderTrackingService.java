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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
     * Get full order tracking information. The caller must present the order's unguessable
     * {@code trackingToken} (audit #17) — the sequential order number alone does not authorize access,
     * so a foreign order is indistinguishable from a non-existent one (both 404).
     */
    @Transactional(readOnly = true)
    public OrderTrackingResponse getOrderTracking(String orderNumber, String token) {
        return buildTrackingResponse(loadTracked(orderNumber, token));
    }

    /**
     * Calculate ETA for an order (token-gated, see {@link #getOrderTracking}).
     */
    @Transactional(readOnly = true)
    public OrderTrackingResponse.ETAInfo calculateETA(String orderNumber, String token) {
        return calculateETAForOrder(loadTracked(orderNumber, token));
    }

    /** Load an order by number only if the supplied tracking token matches; otherwise 404. */
    private Order loadTracked(String orderNumber, String token) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "orderNumber", orderNumber));
        // Constant-time equality — String.equals short-circuits on the first differing character, which
        // leaks a matched prefix to anyone who can time this public endpoint and lets the token be
        // recovered byte by byte. A null/blank or mismatched token is treated as not-found (don't
        // confirm the order exists to someone enumerating order numbers without the secret).
        if (token == null || order.getTrackingToken() == null
                || !java.security.MessageDigest.isEqual(
                        order.getTrackingToken().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        token.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new ResourceNotFoundException("Order", "orderNumber", orderNumber);
        }
        return order;
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
                .createdAt(order.getCreatedAt().toLocalDateTime())
                .confirmedAt(findStatusTimestamp(order, OrderStatus.ACCEPTED))
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
                .timestamp(history.getCreatedAt() != null ? history.getCreatedAt().toLocalDateTime() : null)
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
        LocalDateTime baseTime = order.getCreatedAt().toLocalDateTime();
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
        } else if (order.getStatus() == OrderStatus.ON_DELIVERY) {
            LocalDateTime outAt = findStatusTimestamp(order, OrderStatus.ON_DELIVERY);
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
                .map(h -> h.getCreatedAt() != null ? h.getCreatedAt().toLocalDateTime() : null)
                .findFirst()
                .orElse(null);
    }

    private String getStatusMessage(OrderStatus status) {
        return switch (status) {
            case NEW, PENDING, PLACED -> "Order placed";
            case ACCEPTED -> "Order confirmed by restaurant";
            case PREPARING -> "Kitchen started preparing your order";
            case READY -> "Order is ready";
            case ON_DELIVERY, COURIER_ASSIGNED -> "Driver picked up your order";
            case PICKED_UP -> "Order picked up";
            case DELIVERED -> "Order delivered";
            case COMPLETED -> "Order completed";
            case CANCELLED, REJECTED -> "Order cancelled";
        };
    }
}
