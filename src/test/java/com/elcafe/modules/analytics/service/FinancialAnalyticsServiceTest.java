package com.elcafe.modules.analytics.service;

import com.elcafe.modules.analytics.dto.DailyRevenueDTO;
import com.elcafe.modules.analytics.dto.SalesPerCategoryDTO;
import com.elcafe.modules.financial.service.ShiftTimeService;
import com.elcafe.modules.inventory.service.BatchConsumptionService;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.dto.ProductSalesRow;
import com.elcafe.modules.order.dto.RevenueOrderRow;
import com.elcafe.modules.order.dto.RevenueTotalsRow;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the aggregate-backed FinancialAnalyticsService (PERF-2 rewrite): the service's Java
 * math (grouping, percentages, per-method buckets) operates on repository aggregate rows, so these
 * tests stub rows and pin the arithmetic. Query semantics are pinned separately against a real
 * database in {@code RevenueAggregateQueriesTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinancialAnalyticsServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private BatchConsumptionService batchConsumptionService;
    @Mock private ShiftTimeService shiftTimeService;
    @InjectMocks private FinancialAnalyticsService financialAnalyticsService;

    private static final OffsetDateTime NOW = OffsetDateTime.now(ZoneOffset.UTC);

    private void stubShift() {
        when(shiftTimeService.getShiftTimeRangeForPeriod(anyLong(), any(), any()))
                .thenReturn(new ShiftTimeService.ShiftTimeRange(
                        NOW.minusDays(7), NOW, LocalTime.of(9, 0), LocalTime.of(23, 0)));
        // Calendar-day resolver (what businessDayResolver returns for a restaurant without hours)
        ShiftTimeService.BusinessDayResolver calendarDays = mock(ShiftTimeService.BusinessDayResolver.class);
        when(calendarDays.businessDayFor(any())).thenAnswer(inv ->
                inv.getArgument(0, java.time.LocalDateTime.class).toLocalDate());
        when(shiftTimeService.businessDayResolver(any())).thenReturn(calendarDays);
        when(orderRepository.findRevenueOrderRows(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(orderRepository.sumProductSales(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(orderRepository.sumRevenueTotals(any(), any(), any(), any(), any(), any()))
                .thenReturn(new RevenueTotalsRow(BigDecimal.ZERO, 0L));
    }

    @Test @DisplayName("getDailyRevenue returns empty on no data") void dailyRevenueEmpty() {
        stubShift();
        assertNotNull(financialAnalyticsService.getDailyRevenue(LocalDate.now().minusDays(7), LocalDate.now(), 1L));
    }

    @Test @DisplayName("getDailyRevenue groups rows by day and buckets payment methods") void dailyRevenueMath() {
        stubShift();
        OffsetDateTime day1 = OffsetDateTime.of(2026, 7, 6, 12, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime day2 = OffsetDateTime.of(2026, 7, 7, 12, 0, 0, 0, ZoneOffset.UTC);
        when(orderRepository.findRevenueOrderRows(any(), any(), any(), any(), any(), any())).thenReturn(List.of(
                new RevenueOrderRow(day1, new BigDecimal("100"), PaymentMethod.CASH),
                new RevenueOrderRow(day1, new BigDecimal("50"), PaymentMethod.CARD),
                new RevenueOrderRow(day1, new BigDecimal("30"), null),               // no payment → no bucket
                new RevenueOrderRow(day2, new BigDecimal("40"), PaymentMethod.WALLET))); // wallet counts as online

        List<DailyRevenueDTO> result = financialAnalyticsService
                .getDailyRevenue(LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 7), 1L);

        assertThat(result).hasSize(2);
        DailyRevenueDTO d1 = result.get(0);
        assertThat(d1.getDate()).isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(d1.getTotalRevenue()).isEqualByComparingTo("180");
        assertThat(d1.getTotalOrders()).isEqualTo(3);
        assertThat(d1.getAverageOrderValue()).isEqualByComparingTo("60");
        assertThat(d1.getCashRevenue()).isEqualByComparingTo("100");
        assertThat(d1.getCardRevenue()).isEqualByComparingTo("50");
        assertThat(d1.getOnlineRevenue()).isEqualByComparingTo("0");
        DailyRevenueDTO d2 = result.get(1);
        assertThat(d2.getTotalRevenue()).isEqualByComparingTo("40");
        assertThat(d2.getOnlineRevenue()).isEqualByComparingTo("40");
    }

    @Test @DisplayName("getSalesPerCategory aggregates per-product rows into category figures") void salesPerCategoryMath() {
        stubShift();
        when(orderRepository.sumRevenueTotals(any(), any(), any(), any(), any(), any()))
                .thenReturn(new RevenueTotalsRow(new BigDecimal("200"), 3L));
        when(orderRepository.sumProductSales(any(), any(), any(), any(), any(), any())).thenReturn(List.of(
                new ProductSalesRow(1L, new BigDecimal("120"), 4L),
                new ProductSalesRow(2L, new BigDecimal("40"), 1L)));

        Category drinks = new Category();
        drinks.setId(10L);
        drinks.setName("Drinks");
        Product p1 = new Product(); p1.setId(1L); p1.setCategory(drinks);
        Product p2 = new Product(); p2.setId(2L); p2.setCategory(drinks);
        when(productRepository.findAllById(any())).thenReturn(List.of(p1, p2));

        List<SalesPerCategoryDTO> result = financialAnalyticsService
                .getSalesPerCategory(LocalDate.now().minusDays(7), LocalDate.now(), 1L);

        assertThat(result).hasSize(1);
        SalesPerCategoryDTO cat = result.get(0);
        assertThat(cat.getCategoryId()).isEqualTo(10L);
        assertThat(cat.getCategoryName()).isEqualTo("Drinks");
        assertThat(cat.getTotalRevenue()).isEqualByComparingTo("160");
        assertThat(cat.getTotalItemsSold()).isEqualTo(5);
        assertThat(cat.getNumberOfProducts()).isEqualTo(2);
        // 160 / 200 = 80%
        assertThat(cat.getPercentageOfTotalRevenue()).isEqualByComparingTo("80");
        // 160 / 5 items = 32
        assertThat(cat.getAverageItemPrice()).isEqualByComparingTo("32");
    }
}
