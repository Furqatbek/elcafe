package com.elcafe.bff.pos.service;

import com.elcafe.bff.pos.dto.POSDashboardDTO;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.Table;
import com.elcafe.modules.restaurant.repository.TableRepository;
import com.elcafe.modules.settings.entity.PrinterSettings;
import com.elcafe.modules.settings.repository.PrinterSettingsRepository;
import com.elcafe.modules.settings.repository.PrintJobRepository;
import com.elcafe.modules.settings.entity.PrintJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * BFF Assembler for POS Dashboard.
 * Aggregates data from multiple services into a single DTO for the POS terminal.
 */
@Service
@RequiredArgsConstructor
public class POSDashboardAssembler {

    private final OrderRepository orderRepository;
    private final TableRepository tableRepository;
    private final PrinterSettingsRepository printerSettingsRepository;
    private final PrintJobRepository printJobRepository;

    @Transactional(readOnly = true)
    public POSDashboardDTO assembleDashboard(Restaurant restaurant) {
        Long restaurantId = restaurant.getId();
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        return POSDashboardDTO.builder()
                .restaurant(assembleRestaurantInfo(restaurant))
                .todaySales(assembleSalesOverview(restaurantId, todayStart))
                .activeOrders(assembleActiveOrders(restaurantId))
                .tableStatuses(assembleTableStatuses(restaurantId))
                .printerStatuses(assemblePrinterStatuses(restaurantId))
                .alerts(assembleAlerts(restaurantId))
                .serverTime(now)
                .build();
    }

    private POSDashboardDTO.RestaurantInfo assembleRestaurantInfo(Restaurant restaurant) {
        return POSDashboardDTO.RestaurantInfo.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .timezone(restaurant.getTimezone() != null ? restaurant.getTimezone() : "UTC")
                .isOpen(restaurant.getIsOpen())
                .currency(restaurant.getCurrencyCode() != null ? restaurant.getCurrencyCode() : "USD")
                .currencySymbol(restaurant.getCurrencySymbol() != null ? restaurant.getCurrencySymbol() : "$")
                .build();
    }

    private POSDashboardDTO.SalesOverview assembleSalesOverview(Long restaurantId, LocalDateTime todayStart) {
        List<Order> todayOrders = orderRepository.findByRestaurantIdAndCreatedAtAfter(restaurantId, todayStart);

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalTips = BigDecimal.ZERO;
        int pendingCount = 0;
        int completedCount = 0;

        for (Order order : todayOrders) {
            if (order.getStatus() == OrderStatus.COMPLETED) {
                totalRevenue = totalRevenue.add(order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO);
                totalTips = totalTips.add(order.getTip() != null ? order.getTip() : BigDecimal.ZERO);
                completedCount++;
            } else if (order.getStatus() == OrderStatus.PENDING ||
                       order.getStatus() == OrderStatus.CONFIRMED ||
                       order.getStatus() == OrderStatus.PREPARING) {
                pendingCount++;
            }
        }

        BigDecimal averageOrderValue = completedCount > 0
                ? totalRevenue.divide(BigDecimal.valueOf(completedCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return POSDashboardDTO.SalesOverview.builder()
                .totalRevenue(totalRevenue)
                .orderCount(todayOrders.size())
                .averageOrderValue(averageOrderValue)
                .pendingOrders(pendingCount)
                .completedOrders(completedCount)
                .totalTips(totalTips)
                .build();
    }

    private List<POSDashboardDTO.ActiveOrderSummary> assembleActiveOrders(Long restaurantId) {
        List<OrderStatus> activeStatuses = List.of(
                OrderStatus.PENDING,
                OrderStatus.CONFIRMED,
                OrderStatus.PREPARING,
                OrderStatus.READY
        );

        return orderRepository.findByRestaurantIdAndStatusIn(restaurantId, activeStatuses)
                .stream()
                .map(this::mapToActiveOrderSummary)
                .collect(Collectors.toList());
    }

    private POSDashboardDTO.ActiveOrderSummary mapToActiveOrderSummary(Order order) {
        LocalDateTime now = LocalDateTime.now();
        long minutesElapsed = ChronoUnit.MINUTES.between(order.getCreatedAt(), now);
        boolean isUrgent = minutesElapsed > 30 && order.getStatus() != OrderStatus.READY;

        String tableNumber = null;
        if (order.getTables() != null && !order.getTables().isEmpty()) {
            tableNumber = order.getTables().stream()
                    .map(Table::getTableNumber)
                    .collect(Collectors.joining(", "));
        }

        return POSDashboardDTO.ActiveOrderSummary.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus().name())
                .orderType(order.getOrderType() != null ? order.getOrderType().name() : null)
                .customerName(order.getCustomerName())
                .tableNumber(tableNumber)
                .total(order.getTotal())
                .itemCount(order.getItems() != null ? order.getItems().size() : 0)
                .createdAt(order.getCreatedAt())
                .minutesElapsed((int) minutesElapsed)
                .isUrgent(isUrgent)
                .build();
    }

    private List<POSDashboardDTO.TableStatusDTO> assembleTableStatuses(Long restaurantId) {
        return tableRepository.findByRestaurantId(restaurantId)
                .stream()
                .map(this::mapToTableStatus)
                .collect(Collectors.toList());
    }

    private POSDashboardDTO.TableStatusDTO mapToTableStatus(Table table) {
        return POSDashboardDTO.TableStatusDTO.builder()
                .tableId(table.getId())
                .tableNumber(table.getTableNumber())
                .status(table.getStatus() != null ? table.getStatus().name() : "AVAILABLE")
                .currentOrderId(table.getCurrentOrderId())
                .guestCount(table.getGuestCount())
                .occupiedSince(table.getOccupiedSince())
                .build();
    }

    private List<POSDashboardDTO.PrinterStatusDTO> assemblePrinterStatuses(Long restaurantId) {
        return printerSettingsRepository.findByRestaurantId(restaurantId)
                .stream()
                .map(printer -> mapToPrinterStatus(printer, restaurantId))
                .collect(Collectors.toList());
    }

    private POSDashboardDTO.PrinterStatusDTO mapToPrinterStatus(PrinterSettings printer, Long restaurantId) {
        long pendingJobs = printJobRepository.countByRestaurant_IdAndStatus(
                restaurantId, PrintJob.PrintJobStatus.PENDING);
        long failedJobs = printJobRepository.countDeadLetterJobs(restaurantId);

        return POSDashboardDTO.PrinterStatusDTO.builder()
                .printerId(printer.getId())
                .printerName(printer.getName())
                .printerType(printer.getPrinterType() != null ? printer.getPrinterType().name() : null)
                .isOnline(printer.getIsEnabled())
                .pendingJobs((int) pendingJobs)
                .failedJobs((int) failedJobs)
                .build();
    }

    private List<POSDashboardDTO.AlertDTO> assembleAlerts(Long restaurantId) {
        List<POSDashboardDTO.AlertDTO> alerts = new ArrayList<>();

        // Check for printer issues
        long dlqJobs = printJobRepository.countDeadLetterJobs(restaurantId);
        if (dlqJobs > 0) {
            alerts.add(POSDashboardDTO.AlertDTO.builder()
                    .alertType("PRINT_FAILURE")
                    .severity("ERROR")
                    .title("Print Jobs Failed")
                    .message(dlqJobs + " print job(s) failed and need attention")
                    .actionUrl("/settings/printers/queue")
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        // Check for delayed orders
        LocalDateTime delayThreshold = LocalDateTime.now().minusMinutes(30);
        List<OrderStatus> inProgressStatuses = List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED);
        List<Order> delayedOrders = orderRepository.findByRestaurantIdAndStatusIn(restaurantId, inProgressStatuses)
                .stream()
                .filter(o -> o.getCreatedAt().isBefore(delayThreshold))
                .toList();

        if (!delayedOrders.isEmpty()) {
            alerts.add(POSDashboardDTO.AlertDTO.builder()
                    .alertType("ORDER_DELAYED")
                    .severity("WARNING")
                    .title("Delayed Orders")
                    .message(delayedOrders.size() + " order(s) waiting for more than 30 minutes")
                    .actionUrl("/orders?filter=delayed")
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        return alerts;
    }
}
