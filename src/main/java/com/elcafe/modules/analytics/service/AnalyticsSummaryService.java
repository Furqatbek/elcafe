package com.elcafe.modules.analytics.service;

import com.elcafe.modules.analytics.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Service that aggregates analytics from all sources
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsSummaryService {

    private final FinancialAnalyticsService financialAnalyticsService;
    private final OperationalAnalyticsService operationalAnalyticsService;
    private final CustomerAnalyticsService customerAnalyticsService;
    private final InventoryAnalyticsService inventoryAnalyticsService;

    /**
     * Get comprehensive analytics summary
     */
    public AnalyticsSummaryDTO getAnalyticsSummary(
            LocalDate startDate, LocalDate endDate, Long restaurantId,
            BigDecimal laborCosts, BigDecimal operatingExpenses) {

        // Financial metrics
        List<DailyRevenueDTO> dailyRevenue = financialAnalyticsService.getDailyRevenue(startDate, endDate, restaurantId);

        BigDecimal totalRevenue = dailyRevenue.stream()
                .map(DailyRevenueDTO::getTotalRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalOrders = dailyRevenue.stream()
                .mapToInt(DailyRevenueDTO::getTotalOrders)
                .sum();

        BigDecimal averageOrderValue = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        COGSAnalyticsDTO cogsAnalytics = financialAnalyticsService.getCOGSAnalytics(startDate, endDate, restaurantId);

        ProfitabilityAnalyticsDTO profitability = financialAnalyticsService.getProfitabilityAnalytics(
                startDate, endDate, restaurantId, laborCosts, operatingExpenses);

        // Operational metrics
        OrderTimingAnalyticsDTO timing = operationalAnalyticsService.getOrderTimingAnalytics(startDate, endDate, restaurantId);
        PeakHoursDTO peakHours = operationalAnalyticsService.getPeakHours(startDate, endDate, restaurantId);

        // Customer metrics
        CustomerRetentionDTO retention = customerAnalyticsService.getCustomerRetention(startDate, endDate, restaurantId);
        CustomerLTVDTO ltv = customerAnalyticsService.getCustomerLTV(restaurantId);
        CustomerSatisfactionDTO satisfaction = customerAnalyticsService.getCustomerSatisfaction(startDate, endDate, restaurantId);

        // Inventory metrics
        InventoryTurnoverDTO inventoryTurnover = inventoryAnalyticsService.getInventoryTurnover(startDate, endDate, restaurantId);

        // Safe null handling for inventory turnovers
        List<InventoryTurnoverDTO.IngredientTurnover> ingredientTurnovers =
                inventoryTurnover != null && inventoryTurnover.getIngredientTurnovers() != null
                        ? inventoryTurnover.getIngredientTurnovers()
                        : List.of();

        long lowStockItems = ingredientTurnovers.stream()
                .filter(ingredient -> ingredient != null
                        && ingredient.getAverageStock() != null
                        && (ingredient.getAverageStock().compareTo(BigDecimal.ZERO) == 0
                            || ingredient.getDaysToSellInventory() > 60))
                .count();

        // Safe null handling for peak hours
        List<Integer> peakHoursList = peakHours != null && peakHours.getPeakHours() != null
                ? peakHours.getPeakHours()
                : List.of();

        Integer peakHourStart = peakHoursList.isEmpty() ? 12 : peakHoursList.get(0);
        Integer peakHourEnd = peakHoursList.isEmpty() ? 13 : peakHoursList.get(peakHoursList.size() - 1);

        return AnalyticsSummaryDTO.builder()
                .startDate(startDate)
                .endDate(endDate)
                // Financial
                .totalRevenue(totalRevenue)
                .averageOrderValue(averageOrderValue)
                .totalCOGS(cogsAnalytics.getTotalCOGS())
                .grossProfit(profitability.getGrossProfit())
                .grossProfitMargin(profitability.getGrossProfitMargin())
                .netProfit(profitability.getNetProfit())
                .netProfitMargin(profitability.getNetProfitMargin())
                // Operational
                .totalOrders(totalOrders)
                .averagePreparationTime(timing.getAveragePreparationTimeMinutes())
                .averageDeliveryTime(timing.getAverageDeliveryTimeMinutes())
                .peakHourStart(peakHourStart)
                .peakHourEnd(peakHourEnd)
                // Customer
                .totalCustomers(retention.getCustomersAtEnd())
                .newCustomers(retention.getNewCustomers())
                .retentionRate(retention.getRetentionRate())
                .averageCustomerLTV(ltv.getAverageCustomerLTV())
                .satisfactionScore(satisfaction.getOverallSatisfactionScore())
                // Inventory
                .inventoryTurnoverRatio(inventoryTurnover.getOverallTurnoverRatio())
                .lowStockItems((int) lowStockItems)
                .build();
    }
}
