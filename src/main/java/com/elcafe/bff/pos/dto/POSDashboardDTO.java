package com.elcafe.bff.pos.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * BFF DTO for POS Dashboard view.
 * Aggregates data from multiple services into a single response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class POSDashboardDTO {

    private RestaurantInfo restaurant;
    private SalesOverview todaySales;
    private List<ActiveOrderSummary> activeOrders;
    private List<TableStatusDTO> tableStatuses;
    private List<PrinterStatusDTO> printerStatuses;
    private List<AlertDTO> alerts;
    private LocalDateTime serverTime;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RestaurantInfo {
        private Long id;
        private String name;
        private String timezone;
        private Boolean isOpen;
        private String currency;
        private String currencySymbol;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalesOverview {
        private BigDecimal totalRevenue;
        private Integer orderCount;
        private BigDecimal averageOrderValue;
        private Integer pendingOrders;
        private Integer completedOrders;
        private BigDecimal totalTips;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActiveOrderSummary {
        private Long id;
        private String orderNumber;
        private String status;
        private String orderType;
        private String customerName;
        private String tableNumber;
        private BigDecimal total;
        private Integer itemCount;
        private LocalDateTime createdAt;
        private Integer minutesElapsed;
        private Boolean isUrgent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableStatusDTO {
        private Long tableId;
        private String tableNumber;
        private String status; // AVAILABLE, OCCUPIED, RESERVED, CLEANING
        private Long currentOrderId;
        private String orderNumber;
        private Integer guestCount;
        private LocalDateTime occupiedSince;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrinterStatusDTO {
        private Long printerId;
        private String printerName;
        private String printerType;
        private Boolean isOnline;
        private Integer pendingJobs;
        private Integer failedJobs;
        private LocalDateTime lastPrintedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlertDTO {
        private String alertType; // LOW_STOCK, PRINTER_OFFLINE, ORDER_DELAYED, PAYMENT_FAILED
        private String severity; // INFO, WARNING, ERROR
        private String title;
        private String message;
        private String actionUrl;
        private LocalDateTime createdAt;
    }
}
