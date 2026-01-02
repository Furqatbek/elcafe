package com.elcafe.modules.notification.service;

import com.elcafe.modules.financial.entity.Expense;
import com.elcafe.modules.financial.repository.ExpenseRepository;
import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.modules.notification.config.FinancialAlertConfig;
import com.elcafe.modules.notification.entity.FinancialAlertSubscription;
import com.elcafe.modules.notification.repository.FinancialAlertSubscriptionRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Service for generating and sending daily financial reports via Telegram
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyFinancialReportService {

    private final FinancialAlertConfig alertConfig;
    private final TelegramBotService telegramBotService;
    private final FinancialAlertSubscriptionRepository subscriptionRepository;
    private final OrderRepository orderRepository;
    private final ExpenseRepository expenseRepository;
    private final RestaurantRepository restaurantRepository;
    private final ShiftTimeService shiftTimeService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Scheduled task to check and send daily financial reports
     * Runs every 15 minutes to check if any reports need to be sent
     */
    @Scheduled(fixedRateString = "#{${financial-alert.check-interval-minutes:15} * 60000}", initialDelayString = "120000")
    @Transactional
    public void checkAndSendDailyReports() {
        if (!alertConfig.isEnabled()) {
            log.debug("Financial alerts are disabled");
            return;
        }

        log.info("Checking for daily financial reports to send...");

        LocalDate today = LocalDate.now();
        LocalTime currentTime = LocalTime.now();

        // Find all subscriptions ready to send (report time passed, not sent today)
        List<FinancialAlertSubscription> readySubscriptions =
            subscriptionRepository.findReadyToSend(currentTime, today);

        if (readySubscriptions.isEmpty()) {
            log.debug("No financial reports ready to send");
            return;
        }

        // Group by restaurant for efficient processing
        Map<Long, List<FinancialAlertSubscription>> byRestaurant = readySubscriptions.stream()
            .collect(Collectors.groupingBy(s -> s.getRestaurant().getId()));

        for (Map.Entry<Long, List<FinancialAlertSubscription>> entry : byRestaurant.entrySet()) {
            Long restaurantId = entry.getKey();
            List<FinancialAlertSubscription> subscriptions = entry.getValue();

            // Calculate daily metrics once per restaurant
            DailyMetrics metrics = calculateDailyMetrics(restaurantId, today);

            // Send to all subscribers
            for (FinancialAlertSubscription subscription : subscriptions) {
                sendDailyReport(subscription, metrics, today);
            }
        }

        log.info("Daily financial report check completed");
    }

    /**
     * Calculate daily financial metrics for a restaurant based on shift hours.
     * Uses EXACTLY the same logic as FinancialReportsService.generateProfitLossReport()
     * to ensure consistency between Telegram reports and financial reports.
     */
    public DailyMetrics calculateDailyMetrics(Long restaurantId, LocalDate date) {
        // Get shift time range using shared service (same as P&L report)
        ShiftTimeService.ShiftTimeRange shift = shiftTimeService.getShiftTimeRange(restaurantId, date);
        log.info("Calculating metrics for restaurant {} on {} - shift: {} to {}",
            restaurantId, date, shift.start(), shift.end());

        // Get orders for the shift period (same query as P&L report)
        List<Order> orders = orderRepository.findByRestaurant_IdAndCreatedAtBetweenOrderByCreatedAtDesc(
            restaurantId, shift.start(), shift.end());
        log.info("Found {} total orders in shift period", orders.size());

        // Filter to revenue-generating orders (EXACTLY same logic as P&L report):
        // 1. Orders with status in REVENUE_STATUSES (ACCEPTED, PREPARING, READY, etc.)
        // 2. OR orders that are fully paid (regardless of status - handles POS orders)
        // 3. OR orders with PaymentStatus.COMPLETED
        // 4. Exclude CANCELLED orders
        List<Order> completedOrders = orders.stream()
            .filter(o -> o.getStatus() != OrderStatus.CANCELLED)
            .filter(o -> ShiftTimeService.REVENUE_STATUSES.contains(o.getStatus())
                      || o.isFullyPaid()
                      || o.getPaymentStatus() == PaymentStatus.COMPLETED)
            .collect(Collectors.toList());
        log.info("Found {} revenue orders (by status {} or fully paid)", completedOrders.size(), ShiftTimeService.REVENUE_STATUSES);

        // Log order statuses for debugging
        if (orders.size() > 0 && completedOrders.size() == 0) {
            Map<OrderStatus, Long> statusCounts = orders.stream()
                .collect(Collectors.groupingBy(Order::getStatus, Collectors.counting()));
            long paidCount = orders.stream().filter(Order::isFullyPaid).count();
            log.warn("No revenue orders found! Order statuses: {}, Paid orders: {}", statusCounts, paidCount);
        }

        // Calculate revenue breakdown (EXACTLY same as P&L report)
        BigDecimal salesRevenue = completedOrders.stream()
            .map(Order::getSubtotal)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal serviceFeeRevenue = completedOrders.stream()
            .map(Order::getServiceFee)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal deliveryFeeRevenue = completedOrders.stream()
            .map(Order::getDeliveryFee)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal tipRevenue = completedOrders.stream()
            .map(Order::getTipAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRevenue = completedOrders.stream()
            .map(Order::getTotal)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("Revenue breakdown - sales: {}, serviceFee: {}, deliveryFee: {}, tips: {}, total: {}",
            salesRevenue, serviceFeeRevenue, deliveryFeeRevenue, tipRevenue, totalRevenue);

        // Get expenses (EXACTLY same as P&L report - from expense records with PAID filter)
        List<Expense> expenses = expenseRepository.findByRestaurant_IdAndExpenseDateBetween(
            restaurantId, date, date);

        BigDecimal totalExpenses = expenses.stream()
            .filter(e -> e.getPaymentStatus() == Expense.PaymentStatus.PAID)
            .map(Expense::getTotalAmount)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        log.info("Total expenses (PAID only): {}", totalExpenses);

        // Calculate net income (same as P&L report)
        BigDecimal netIncome = totalRevenue.subtract(totalExpenses);

        // Get restaurant name
        String restaurantName = restaurantRepository.findById(restaurantId)
            .map(Restaurant::getName)
            .orElse("Restaurant");

        return new DailyMetrics(
            restaurantName,
            date,
            shift.openTime(),
            shift.closeTime(),
            completedOrders.size(),
            salesRevenue,
            serviceFeeRevenue,
            deliveryFeeRevenue,
            tipRevenue,
            totalRevenue,
            totalExpenses,
            netIncome
        );
    }

    /**
     * Send daily report to a subscriber
     */
    private void sendDailyReport(FinancialAlertSubscription subscription, DailyMetrics metrics, LocalDate reportDate) {
        try {
            String message = formatDailyReport(subscription, metrics);

            boolean sent = telegramBotService.sendMessage(subscription.getTelegramChatId(), message);

            if (sent) {
                subscription.setLastReportSentAt(LocalDateTime.now());
                subscription.setLastReportDate(reportDate);
                subscriptionRepository.save(subscription);

                log.info("Daily financial report sent to chatId {} for restaurant {}",
                    subscription.getTelegramChatId(), metrics.restaurantName());
            }
        } catch (Exception e) {
            log.error("Failed to send daily report to chatId {}: {}",
                subscription.getTelegramChatId(), e.getMessage());
        }
    }

    /**
     * Format the daily report message.
     * Uses the same revenue breakdown structure as the P&L financial report.
     */
    private String formatDailyReport(FinancialAlertSubscription subscription, DailyMetrics metrics) {
        StringBuilder sb = new StringBuilder();

        sb.append("📊 <b>Финансовый отчет за смену</b>\n\n");
        sb.append(String.format("🏪 <b>%s</b>\n", metrics.restaurantName()));
        sb.append(String.format("📅 %s\n", metrics.date().format(DATE_FORMATTER)));
        sb.append(String.format("🕐 Смена: %s - %s\n\n",
            metrics.shiftStart().format(TIME_FORMATTER),
            metrics.shiftEnd().format(TIME_FORMATTER)));

        if (subscription.getAlertDailyRevenue()) {
            sb.append(String.format("💰 <b>Общая выручка:</b> %s\n", formatCurrency(metrics.totalRevenue())));
            sb.append(String.format("📦 Заказов: %d\n\n", metrics.orderCount()));

            // Revenue breakdown (same as P&L report)
            sb.append("<b>Детализация выручки:</b>\n");
            sb.append(String.format("   🍽 Продажи: %s\n", formatCurrency(metrics.salesRevenue())));
            if (metrics.serviceFeeRevenue().compareTo(BigDecimal.ZERO) > 0) {
                sb.append(String.format("   🔧 Сервисный сбор: %s\n", formatCurrency(metrics.serviceFeeRevenue())));
            }
            if (metrics.deliveryFeeRevenue().compareTo(BigDecimal.ZERO) > 0) {
                sb.append(String.format("   🚗 Доставка: %s\n", formatCurrency(metrics.deliveryFeeRevenue())));
            }
            if (metrics.tipRevenue().compareTo(BigDecimal.ZERO) > 0) {
                sb.append(String.format("   💵 Чаевые: %s\n", formatCurrency(metrics.tipRevenue())));
            }
            sb.append("\n");
        }

        if (subscription.getAlertDailyExpenses()) {
            sb.append(String.format("💸 <b>Расходы:</b> %s\n\n", formatCurrency(metrics.totalExpenses())));
        }

        if (subscription.getAlertDailyProfit()) {
            String profitEmoji = metrics.netIncome().compareTo(BigDecimal.ZERO) >= 0 ? "📈" : "📉";
            sb.append(String.format("%s <b>Чистая прибыль:</b> %s\n\n", profitEmoji, formatCurrency(metrics.netIncome())));

            // Calculate profit margin if revenue > 0
            if (metrics.totalRevenue().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal margin = metrics.netIncome()
                    .divide(metrics.totalRevenue(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
                sb.append(String.format("📊 Маржа: %.1f%%\n\n", margin));
            }
        }

        sb.append(String.format("⏰ %s", LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))));

        return sb.toString();
    }

    /**
     * Format currency value
     */
    private String formatCurrency(BigDecimal value) {
        if (value == null) {
            return "0.00";
        }
        return String.format("%,.2f", value);
    }

    /**
     * Manually trigger daily report for a specific restaurant
     */
    @Transactional
    public void triggerReportForRestaurant(Long restaurantId) {
        LocalDate today = LocalDate.now();
        DailyMetrics metrics = calculateDailyMetrics(restaurantId, today);

        List<FinancialAlertSubscription> subscriptions =
            subscriptionRepository.findByRestaurant_IdAndActiveTrue(restaurantId);

        if (subscriptions.isEmpty()) {
            log.info("No active financial alert subscriptions for restaurant: {}", restaurantId);
            return;
        }

        for (FinancialAlertSubscription subscription : subscriptions) {
            sendDailyReport(subscription, metrics, today);
        }

        log.info("Manual financial report triggered for restaurant: {}", metrics.restaurantName());
    }

    /**
     * Get daily metrics summary for a restaurant (for API usage).
     * Returns same structure as P&L financial report for consistency.
     */
    public Map<String, Object> getDailyMetricsSummary(Long restaurantId, LocalDate date) {
        DailyMetrics metrics = calculateDailyMetrics(restaurantId, date);

        return Map.ofEntries(
            Map.entry("restaurantName", metrics.restaurantName()),
            Map.entry("date", metrics.date().toString()),
            Map.entry("shiftStart", metrics.shiftStart().toString()),
            Map.entry("shiftEnd", metrics.shiftEnd().toString()),
            Map.entry("orderCount", metrics.orderCount()),
            // Revenue breakdown (same as P&L report)
            Map.entry("salesRevenue", metrics.salesRevenue()),
            Map.entry("serviceFeeRevenue", metrics.serviceFeeRevenue()),
            Map.entry("deliveryFeeRevenue", metrics.deliveryFeeRevenue()),
            Map.entry("tipRevenue", metrics.tipRevenue()),
            Map.entry("totalRevenue", metrics.totalRevenue()),
            // Expenses and net income (same as P&L report)
            Map.entry("totalExpenses", metrics.totalExpenses()),
            Map.entry("netIncome", metrics.netIncome())
        );
    }

    /**
     * Record class for daily metrics with shift times.
     * Uses the same revenue breakdown structure as FinancialReportsService.ProfitLossReport
     * to ensure consistency between Telegram reports and financial reports.
     */
    public record DailyMetrics(
        String restaurantName,
        LocalDate date,
        LocalTime shiftStart,
        LocalTime shiftEnd,
        int orderCount,
        // Revenue breakdown (same as P&L report)
        BigDecimal salesRevenue,
        BigDecimal serviceFeeRevenue,
        BigDecimal deliveryFeeRevenue,
        BigDecimal tipRevenue,
        BigDecimal totalRevenue,
        // Expenses and net income (same as P&L report)
        BigDecimal totalExpenses,
        BigDecimal netIncome
    ) {}
}
