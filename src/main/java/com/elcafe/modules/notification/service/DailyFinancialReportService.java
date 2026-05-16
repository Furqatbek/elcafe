package com.elcafe.modules.notification.service;

import com.elcafe.modules.financial.service.FinancialReportsService;
import com.elcafe.modules.financial.service.FinancialReportsService.ProfitLossReport;
import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.modules.notification.config.FinancialAlertConfig;
import com.elcafe.modules.notification.entity.FinancialAlertSubscription;
import com.elcafe.modules.notification.repository.FinancialAlertSubscriptionRepository;
import com.elcafe.modules.ownerbot.service.OwnerTelegramBotService;
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
import java.util.stream.Collectors;

/**
 * Service for generating and sending daily financial reports via Telegram
 */
@Slf4j
@Service
public class DailyFinancialReportService {

    private final FinancialAlertConfig alertConfig;
    private final OwnerTelegramBotService ownerBotService;
    private final FinancialAlertSubscriptionRepository subscriptionRepository;
    private final FinancialReportsService financialReportsService;
    private final RestaurantRepository restaurantRepository;
    private final ShiftTimeService shiftTimeService;

    public DailyFinancialReportService(
            FinancialAlertConfig alertConfig,
            @org.springframework.context.annotation.Lazy OwnerTelegramBotService ownerBotService,
            FinancialAlertSubscriptionRepository subscriptionRepository,
            FinancialReportsService financialReportsService,
            RestaurantRepository restaurantRepository,
            ShiftTimeService shiftTimeService) {
        this.alertConfig = alertConfig;
        this.ownerBotService = ownerBotService;
        this.subscriptionRepository = subscriptionRepository;
        this.financialReportsService = financialReportsService;
        this.restaurantRepository = restaurantRepository;
        this.shiftTimeService = shiftTimeService;
    }

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

            // Use shift-aware business day instead of calendar date
            // This handles cases where shift crosses midnight (e.g., 11:00-02:00)
            // At 01:00 AM, this will correctly return yesterday's date as the business day
            LocalDate businessDay = shiftTimeService.getCurrentBusinessDay(restaurantId);

            // Calculate daily metrics once per restaurant
            DailyMetrics metrics = calculateDailyMetrics(restaurantId, businessDay);

            // Send to all subscribers
            for (FinancialAlertSubscription subscription : subscriptions) {
                sendDailyReport(subscription, metrics, businessDay);
            }
        }

        log.info("Daily financial report check completed");
    }

    /**
     * Calculate daily financial metrics for a restaurant (single day).
     * Directly uses FinancialReportsService.generateProfitLossReport() to ensure
     * 100% consistency with the P&L API endpoint.
     */
    public DailyMetrics calculateDailyMetrics(Long restaurantId, LocalDate date) {
        return calculateDailyMetrics(restaurantId, date, date);
    }

    /**
     * Calculate financial metrics for a restaurant with date range.
     * Uses the exact same query as the P&L API:
     * /api/v1/financial/reports/profit-loss?restaurantId=X&startDate=Y&endDate=Z
     */
    public DailyMetrics calculateDailyMetrics(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        log.info("Calculating metrics for restaurant {} from {} to {} using P&L report service",
            restaurantId, startDate, endDate);

        // Use the exact same P&L report that the API uses
        // This ensures Telegram report matches exactly what the financial dashboard shows
        ProfitLossReport plReport = financialReportsService.generateProfitLossReport(restaurantId, startDate, endDate);

        // Get shift times for display (use the period range)
        ShiftTimeService.ShiftTimeRange shift = shiftTimeService.getShiftTimeRangeForPeriod(restaurantId, startDate, endDate);

        // Get restaurant name
        String restaurantName = restaurantRepository.findById(restaurantId)
            .map(Restaurant::getName)
            .orElse("Restaurant");

        log.info("P&L Report for Telegram - orders: {}, revenue: {}, expenses: {}, netIncome: {}",
            plReport.getOrderCount(), plReport.getTotalRevenue(), plReport.getTotalExpenses(), plReport.getNetIncome());

        return new DailyMetrics(
            restaurantName,
            startDate,
            shift.openTime(),
            shift.closeTime(),
            plReport.getOrderCount(),
            plReport.getSalesRevenue() != null ? plReport.getSalesRevenue() : BigDecimal.ZERO,
            plReport.getServiceFeeRevenue() != null ? plReport.getServiceFeeRevenue() : BigDecimal.ZERO,
            plReport.getDeliveryFeeRevenue() != null ? plReport.getDeliveryFeeRevenue() : BigDecimal.ZERO,
            plReport.getTipRevenue() != null ? plReport.getTipRevenue() : BigDecimal.ZERO,
            plReport.getTotalRevenue() != null ? plReport.getTotalRevenue() : BigDecimal.ZERO,
            plReport.getTotalExpenses() != null ? plReport.getTotalExpenses() : BigDecimal.ZERO,
            plReport.getShiftDrawerExpenses() != null ? plReport.getShiftDrawerExpenses() : BigDecimal.ZERO,
            plReport.getOtherExpenses() != null ? plReport.getOtherExpenses() : BigDecimal.ZERO,
            plReport.getTotalPayroll() != null ? plReport.getTotalPayroll() : BigDecimal.ZERO,
            plReport.getNetIncome() != null ? plReport.getNetIncome() : BigDecimal.ZERO
        );
    }

    /**
     * Send daily report to a subscriber (scheduled - updates lastReportDate)
     */
    private void sendDailyReport(FinancialAlertSubscription subscription, DailyMetrics metrics, LocalDate reportDate) {
        sendReport(subscription, metrics, reportDate, true);
    }

    /**
     * Send daily report to a subscriber (manual - does NOT update lastReportDate)
     */
    private void sendManualReport(FinancialAlertSubscription subscription, DailyMetrics metrics, LocalDate reportDate) {
        sendReport(subscription, metrics, reportDate, false);
    }

    /**
     * Send report to a subscriber
     * @param updateLastReportDate if true, updates lastReportDate to prevent duplicate scheduled sends
     */
    private void sendReport(FinancialAlertSubscription subscription, DailyMetrics metrics,
                           LocalDate reportDate, boolean updateLastReportDate) {
        try {
            String message = formatDailyReport(subscription, metrics);

            Integer messageId = ownerBotService.sendMessage(subscription.getTelegramChatId(), message);

            if (messageId != null) {
                subscription.setLastReportSentAt(LocalDateTime.now());
                if (updateLastReportDate) {
                    // Use CALENDAR date (not business day) to prevent duplicate sends
                    // For shifts crossing midnight, business day might be yesterday,
                    // but we should only send one report per calendar day
                    subscription.setLastReportDate(LocalDate.now());
                }
                subscriptionRepository.save(subscription);

                String reportType = updateLastReportDate ? "Scheduled" : "Manual";
                log.info("{} financial report sent to chatId {} for restaurant {} (business day: {})",
                    reportType, subscription.getTelegramChatId(), metrics.restaurantName(), reportDate);
            }
        } catch (Exception e) {
            log.error("Failed to send report to chatId {}: {}",
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
            sb.append(String.format("💸 <b>Расходы:</b> %s\n", formatCurrency(metrics.totalExpenses())));
            if (metrics.shiftDrawerExpenses().compareTo(BigDecimal.ZERO) > 0) {
                sb.append(String.format("   🪙 Из кассы смены: %s\n", formatCurrency(metrics.shiftDrawerExpenses())));
            }
            if (metrics.otherExpenses().compareTo(BigDecimal.ZERO) > 0) {
                sb.append(String.format("   🏦 Прочие: %s\n", formatCurrency(metrics.otherExpenses())));
            }
            if (metrics.totalPayroll().compareTo(BigDecimal.ZERO) > 0) {
                sb.append(String.format("   👥 Зарплата: %s\n", formatCurrency(metrics.totalPayroll())));
            }
            sb.append("\n");
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
     * Manually trigger daily report for a specific restaurant.
     * Does NOT update lastReportDate, so scheduled reports will still be sent at the configured time.
     */
    @Transactional
    public void triggerReportForRestaurant(Long restaurantId) {
        // Use shift-aware business day instead of calendar date
        LocalDate businessDay = shiftTimeService.getCurrentBusinessDay(restaurantId);
        DailyMetrics metrics = calculateDailyMetrics(restaurantId, businessDay);

        List<FinancialAlertSubscription> subscriptions =
            subscriptionRepository.findByRestaurant_IdAndActiveTrue(restaurantId);

        if (subscriptions.isEmpty()) {
            log.info("No active financial alert subscriptions for restaurant: {}", restaurantId);
            return;
        }

        for (FinancialAlertSubscription subscription : subscriptions) {
            sendManualReport(subscription, metrics, businessDay);
        }

        log.info("Manual financial report triggered for restaurant: {}", metrics.restaurantName());
    }

    /**
     * Get daily metrics summary for a restaurant (for API usage).
     * Returns same structure as P&L financial report for consistency.
     */
    public Map<String, Object> getDailyMetricsSummary(Long restaurantId, LocalDate date) {
        return getDailyMetricsSummary(restaurantId, date, date);
    }

    /**
     * Get metrics summary for a restaurant with date range (for API usage).
     * Uses the exact same query as the P&L API for consistency.
     */
    public Map<String, Object> getDailyMetricsSummary(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        DailyMetrics metrics = calculateDailyMetrics(restaurantId, startDate, endDate);

        return Map.ofEntries(
            Map.entry("restaurantName", metrics.restaurantName()),
            Map.entry("startDate", startDate.toString()),
            Map.entry("endDate", endDate.toString()),
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
            Map.entry("shiftDrawerExpenses", metrics.shiftDrawerExpenses()),
            Map.entry("otherExpenses", metrics.otherExpenses()),
            Map.entry("totalPayroll", metrics.totalPayroll()),
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
        // Expenses and net income (same as P&L report).
        // shiftDrawerExpenses + otherExpenses == totalExpenses.
        BigDecimal totalExpenses,
        BigDecimal shiftDrawerExpenses,
        BigDecimal otherExpenses,
        BigDecimal totalPayroll,
        BigDecimal netIncome
    ) {}
}
