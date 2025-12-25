package com.elcafe.modules.notification.service;

import com.elcafe.modules.financial.repository.ExpenseRepository;
import com.elcafe.modules.notification.config.FinancialAlertConfig;
import com.elcafe.modules.notification.entity.FinancialAlertSubscription;
import com.elcafe.modules.notification.repository.FinancialAlertSubscriptionRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for generating and sending daily financial reports via Telegram
 */
@Slf4j
@Service
@Lazy
@RequiredArgsConstructor
public class DailyFinancialReportService {

    private final FinancialAlertConfig alertConfig;
    private final TelegramBotService telegramBotService;
    private final FinancialAlertSubscriptionRepository subscriptionRepository;
    private final OrderRepository orderRepository;
    private final ExpenseRepository expenseRepository;
    private final RestaurantRepository restaurantRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    // Order statuses that count as completed/revenue-generating
    // Must match DashboardService.COMPLETED_STATUSES for consistency
    private static final Set<OrderStatus> COMPLETED_ORDER_STATUSES = Set.of(
            OrderStatus.COMPLETED,
            OrderStatus.DELIVERED,
            OrderStatus.READY,
            OrderStatus.PICKED_UP
    );

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
     * Calculate daily financial metrics for a restaurant
     */
    public DailyMetrics calculateDailyMetrics(Long restaurantId, LocalDate date) {
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(23, 59, 59);

        // Get completed orders for the day
        List<Order> orders = orderRepository.findByRestaurant_IdAndCreatedAtBetweenOrderByCreatedAtDesc(
            restaurantId, startOfDay, endOfDay);

        // Filter to only completed/delivered/picked-up orders
        List<Order> completedOrders = orders.stream()
            .filter(o -> COMPLETED_ORDER_STATUSES.contains(o.getStatus()))
            .collect(Collectors.toList());

        // Calculate total revenue
        BigDecimal revenue = completedOrders.stream()
            .map(Order::getTotal)
            .filter(t -> t != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate revenue by payment method
        BigDecimal cashRevenue = BigDecimal.ZERO;
        BigDecimal cardRevenue = BigDecimal.ZERO;

        for (Order order : completedOrders) {
            if (order.getPayments() != null) {
                for (Payment payment : order.getPayments()) {
                    // Only count completed/successful payments
                    if (payment.getStatus() == PaymentStatus.COMPLETED && payment.getAmount() != null) {
                        PaymentMethod method = payment.getMethod();
                        if (method == PaymentMethod.CASH) {
                            cashRevenue = cashRevenue.add(payment.getAmount());
                        } else if (method == PaymentMethod.CARD ||
                                   method == PaymentMethod.CREDIT_CARD ||
                                   method == PaymentMethod.DEBIT_CARD) {
                            cardRevenue = cardRevenue.add(payment.getAmount());
                        }
                    }
                }
            }
        }

        // Get expenses for the day
        BigDecimal expenses = expenseRepository.getTotalExpensesByDateRange(restaurantId, date, date);
        if (expenses == null) {
            expenses = BigDecimal.ZERO;
        }

        // Calculate profit (Revenue - Expenses)
        BigDecimal profit = revenue.subtract(expenses);

        // Get restaurant name
        String restaurantName = restaurantRepository.findById(restaurantId)
            .map(Restaurant::getName)
            .orElse("Restaurant");

        return new DailyMetrics(
            restaurantName,
            date,
            completedOrders.size(),
            revenue,
            cashRevenue,
            cardRevenue,
            expenses,
            profit
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
     * Format the daily report message
     */
    private String formatDailyReport(FinancialAlertSubscription subscription, DailyMetrics metrics) {
        StringBuilder sb = new StringBuilder();

        sb.append("📊 <b>Ежедневный финансовый отчет</b>\n\n");
        sb.append(String.format("🏪 <b>%s</b>\n", metrics.restaurantName()));
        sb.append(String.format("📅 %s\n\n", metrics.date().format(DATE_FORMATTER)));

        if (subscription.getAlertDailyRevenue()) {
            sb.append(String.format("💰 <b>Выручка:</b> %s\n", formatCurrency(metrics.revenue())));
            sb.append(String.format("📦 Заказов: %d\n\n", metrics.orderCount()));

            // Payment method breakdown
            sb.append("<b>По способу оплаты:</b>\n");
            sb.append(String.format("   💵 Наличные: %s\n", formatCurrency(metrics.cashRevenue())));
            sb.append(String.format("   💳 Карта: %s\n\n", formatCurrency(metrics.cardRevenue())));
        }

        if (subscription.getAlertDailyExpenses()) {
            sb.append(String.format("💸 <b>Расходы:</b> %s\n\n", formatCurrency(metrics.expenses())));
        }

        if (subscription.getAlertDailyProfit()) {
            String profitEmoji = metrics.profit().compareTo(BigDecimal.ZERO) >= 0 ? "📈" : "📉";
            sb.append(String.format("%s <b>Прибыль:</b> %s\n\n", profitEmoji, formatCurrency(metrics.profit())));

            // Calculate profit margin if revenue > 0
            if (metrics.revenue().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal margin = metrics.profit()
                    .divide(metrics.revenue(), 4, RoundingMode.HALF_UP)
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
     * Get daily metrics summary for a restaurant (for API usage)
     */
    public Map<String, Object> getDailyMetricsSummary(Long restaurantId, LocalDate date) {
        DailyMetrics metrics = calculateDailyMetrics(restaurantId, date);

        return Map.ofEntries(
            Map.entry("restaurantName", metrics.restaurantName()),
            Map.entry("date", metrics.date().toString()),
            Map.entry("orderCount", metrics.orderCount()),
            Map.entry("revenue", metrics.revenue()),
            Map.entry("cashRevenue", metrics.cashRevenue()),
            Map.entry("cardRevenue", metrics.cardRevenue()),
            Map.entry("expenses", metrics.expenses()),
            Map.entry("profit", metrics.profit())
        );
    }

    /**
     * Record class for daily metrics
     */
    public record DailyMetrics(
        String restaurantName,
        LocalDate date,
        int orderCount,
        BigDecimal revenue,
        BigDecimal cashRevenue,
        BigDecimal cardRevenue,
        BigDecimal expenses,
        BigDecimal profit
    ) {}
}
