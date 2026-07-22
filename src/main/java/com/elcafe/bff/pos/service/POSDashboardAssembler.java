package com.elcafe.bff.pos.service;

import com.elcafe.bff.pos.dto.POSDashboardDTO;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
    private final RestaurantTableRepository tableRepository;
    private final PrinterSettingsRepository printerSettingsRepository;
    private final PrintJobRepository printJobRepository;

    @Transactional(readOnly = true)
    public POSDashboardDTO assembleDashboard(Restaurant restaurant) {
        Long restaurantId = restaurant.getId();
        OffsetDateTime todayStart = LocalDate.now().atStartOfDay().atOffset(ZoneOffset.UTC);
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
                .timezone("UTC")  // Restaurant entity doesn't have timezone field
                .isOpen(restaurant.getAcceptingOrders())  // Use acceptingOrders field
                .currency("USD")  // Restaurant entity doesn't have currency fields
                .currencySymbol("$")
                .build();
    }

    private POSDashboardDTO.SalesOverview assembleSalesOverview(Long restaurantId, OffsetDateTime todayStart) {
        List<Order> todayOrders = orderRepository.findByRestaurantIdAndCreatedAtAfter(restaurantId, todayStart);

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalTips = BigDecimal.ZERO;
        int pendingCount = 0;
        int completedCount = 0;

        for (Order order : todayOrders) {
            if (order.getStatus() == OrderStatus.COMPLETED) {
                totalRevenue = totalRevenue.add(order.getTotal() != null ? order.getTotal() : BigDecimal.ZERO);
                totalTips = totalTips.add(order.getTipAmount() != null ? order.getTipAmount() : BigDecimal.ZERO);
                completedCount++;
            } else if (order.getStatus() == OrderStatus.PENDING ||
                       order.getStatus() == OrderStatus.ACCEPTED ||
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
                OrderStatus.ACCEPTED,
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
        LocalDateTime createdAt = order.getCreatedAt() != null
                ? order.getCreatedAt().toLocalDateTime()
                : now;
        long minutesElapsed = ChronoUnit.MINUTES.between(createdAt, now);
        boolean isUrgent = minutesElapsed > 30 && order.getStatus() != OrderStatus.READY;

        String tableNumber = null;
        if (order.getTables() != null && !order.getTables().isEmpty()) {
            tableNumber = order.getTables().stream()
                    .map(RestaurantTable::getTableNumber)
                    .collect(Collectors.joining(", "));
        }

        // Get customer name from customer entity if available
        String customerName = null;
        if (order.getCustomer() != null) {
            customerName = order.getCustomer().getFullName();  // Use getFullName() instead of getName()
        }

        return POSDashboardDTO.ActiveOrderSummary.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus().name())
                .orderType(order.getOrderType() != null ? order.getOrderType().name() : null)
                .customerName(customerName)
                .tableNumber(tableNumber)
                .total(order.getTotal())
                .itemCount(order.getItems() != null ? order.getItems().size() : 0)
                .createdAt(createdAt)
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

    private POSDashboardDTO.TableStatusDTO mapToTableStatus(RestaurantTable table) {
        // Get current order ID from the orders relationship if table is occupied
        Long currentOrderId = null;
        if (table.getStatus() == RestaurantTable.TableStatus.OCCUPIED &&
            table.getOrders() != null && !table.getOrders().isEmpty()) {
            // Find the most recent active order
            currentOrderId = table.getOrders().stream()
                    .filter(o -> o.getStatus() != OrderStatus.COMPLETED &&
                                 o.getStatus() != OrderStatus.CANCELLED)
                    .map(Order::getId)
                    .findFirst()
                    .orElse(null);
        }

        return POSDashboardDTO.TableStatusDTO.builder()
                .tableId(table.getId())
                .tableNumber(table.getTableNumber())
                .status(table.getStatus() != null ? table.getStatus().name() : "AVAILABLE")
                .currentOrderId(currentOrderId)
                .guestCount(table.getCapacity())  // Use capacity as guest count proxy
                .occupiedSince(null)  // Field not available on RestaurantTable
                .build();
    }

    private List<POSDashboardDTO.PrinterStatusDTO> assemblePrinterStatuses(Long restaurantId) {
        return printerSettingsRepository.findByRestaurant_Id(restaurantId)
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
                .printerName(printer.getPrinterName())  // Use printerName field
                .printerType(printer.getPrinterType() != null ? printer.getPrinterType().name() : null)
                .isOnline(printer.getEnabled())  // Use enabled field
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
        List<OrderStatus> inProgressStatuses = List.of(OrderStatus.PENDING, OrderStatus.ACCEPTED);
        List<Order> allInProgressOrders = orderRepository.findByRestaurantIdAndStatusIn(restaurantId, inProgressStatuses);
        LocalDateTime delayThreshold = LocalDateTime.now().minusMinutes(30);

        List<Order> delayedOrders = allInProgressOrders.stream()
                .filter(o -> o.getCreatedAt() != null &&
                            o.getCreatedAt().toLocalDateTime().isBefore(delayThreshold))
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
