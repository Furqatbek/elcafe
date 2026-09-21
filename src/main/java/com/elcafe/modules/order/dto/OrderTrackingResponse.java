package com.elcafe.modules.order.dto;

import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.OrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTrackingResponse {

    private String orderNumber;
    private OrderStatus status;
    private OrderType orderType;

    // Restaurant info
    private String restaurantName;
    private String restaurantPhone;
    private String restaurantAddress;

    // Order details
    private BigDecimal totalAmount;
    private Integer itemCount;
    private List<OrderItemSummary> items;

    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime preparingAt;
    private LocalDateTime readyAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime completedAt;

    // Status history
    private List<StatusUpdate> statusHistory;

    // ETA
    private ETAInfo eta;

    // Delivery info (for delivery orders)
    private DeliveryInfo deliveryInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemSummary {
        private String name;
        private Integer quantity;
        private BigDecimal price;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusUpdate {
        private OrderStatus status;
        private LocalDateTime timestamp;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ETAInfo {
        private LocalDateTime estimatedTime;
        private Integer minutesRemaining;
        private String etaMessage;
        private boolean isDelayed;
        private String delayReason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryInfo {
        private String deliveryAddress;
        private String courierName;
        private String courierPhone;
        private Double courierLatitude;
        private Double courierLongitude;
        private String deliveryStatus;
    }

    /**
     * Get a user-friendly status message
     */
    public String getStatusMessage() {
        if (status == null) return "Unknown status";

        return switch (status) {
            case NEW, PENDING, PLACED -> "Order received";
            case ACCEPTED -> "Order confirmed";
            case PREPARING -> "Preparing your order";
            case READY -> orderType == OrderType.DELIVERY ? "Ready for pickup by driver" : "Ready for pickup";
            case ON_DELIVERY, COURIER_ASSIGNED -> "On the way";
            case PICKED_UP -> "Picked up";
            case DELIVERED -> "Delivered";
            case COMPLETED -> "Completed";
            case CANCELLED, REJECTED -> "Cancelled";
        };
    }

    /**
     * Get progress percentage (0-100)
     */
    public int getProgressPercent() {
        if (status == null) return 0;

        return switch (status) {
            case NEW, PENDING, PLACED -> 10;
            case ACCEPTED -> 25;
            case PREPARING -> 50;
            case READY -> 75;
            case ON_DELIVERY, COURIER_ASSIGNED, PICKED_UP -> 85;
            case DELIVERED, COMPLETED -> 100;
            case CANCELLED, REJECTED -> 0;
        };
    }
}
