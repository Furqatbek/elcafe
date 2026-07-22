package com.elcafe.modules.waiter.service;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.waiter.dto.WaiterKPIConfigRequest;
import com.elcafe.modules.waiter.dto.WaiterLeaderboardEntry;
import com.elcafe.modules.waiter.dto.WaiterPerformanceSummary;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.entity.WaiterKPIConfig;
import com.elcafe.modules.waiter.entity.WaiterPerformance;
import com.elcafe.modules.waiter.repository.WaiterCommissionRepository;
import com.elcafe.modules.waiter.repository.WaiterKPIConfigRepository;
import com.elcafe.modules.waiter.repository.WaiterPerformanceRepository;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createRestaurant;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createWaiter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaiterPerformanceServiceTest {

    @Mock private WaiterPerformanceRepository performanceRepository;
    @Mock private WaiterKPIConfigRepository kpiConfigRepository;
    @Mock private WaiterRepository waiterRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private WaiterCommissionRepository commissionRepository;

    @InjectMocks private WaiterPerformanceService performanceService;

    @Captor private ArgumentCaptor<WaiterPerformance> performanceCaptor;
    @Captor private ArgumentCaptor<WaiterKPIConfig> kpiConfigCaptor;

    private Waiter waiter;
    private Restaurant restaurant;

    private static final Long WAITER_ID = 1L;
    private static final Long RESTAURANT_ID = 1L;

    @BeforeEach
    void setUp() {
        waiter = createWaiter();
        restaurant = createRestaurant();
    }

    // ==================== Helper Methods ====================

    private WaiterPerformance createPerformance(Long waiterId, LocalDate date) {
        return WaiterPerformance.builder()
                .id(1L)
                .waiter(waiter)
                .restaurant(restaurant)
                .performanceDate(date)
                .totalOrders(0)
                .totalTablesServed(0)
                .totalCustomersServed(0)
                .totalRevenue(BigDecimal.ZERO)
                .totalTips(BigDecimal.ZERO)
                .avgTicketValue(BigDecimal.ZERO)
                .avgServiceTimeMinutes(0)
                .complaintsCount(0)
                .complimentsCount(0)
                .ratingsCount(0)
                .upsellAttempts(0)
                .upsellSuccesses(0)
                .dessertOrders(0)
                .beverageOrders(0)
                .voidItemsCount(0)
                .voidItemsValue(BigDecimal.ZERO)
                .discountsGiven(0)
                .discountsValue(BigDecimal.ZERO)
                .bonusEarned(BigDecimal.ZERO)
                .build();
    }

    private WaiterPerformance createPerformanceWithOrders(int orders, BigDecimal revenue) {
        WaiterPerformance perf = createPerformance(WAITER_ID, LocalDate.now());
        perf.setTotalOrders(orders);
        perf.setTotalRevenue(revenue);
        if (orders > 0) {
            perf.setAvgTicketValue(revenue.divide(BigDecimal.valueOf(orders), 2, RoundingMode.HALF_UP));
        }
        return perf;
    }

    private void stubExistingPerformance(WaiterPerformance existing) {
        when(performanceRepository.findByWaiterIdAndPerformanceDate(eq(WAITER_ID), any(LocalDate.class)))
                .thenReturn(Optional.of(existing));
        when(performanceRepository.save(any(WaiterPerformance.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void stubNewPerformanceCreation() {
        when(performanceRepository.findByWaiterIdAndPerformanceDate(eq(WAITER_ID), any(LocalDate.class)))
                .thenReturn(Optional.empty());
        when(waiterRepository.findById(WAITER_ID)).thenReturn(Optional.of(waiter));
        when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
        when(performanceRepository.save(any(WaiterPerformance.class)))
                .thenAnswer(invocation -> {
                    WaiterPerformance p = invocation.getArgument(0);
                    if (p.getId() == null) {
                        p.setId(1L);
                    }
                    return p;
                });
    }

    private void stubKPIConfigEmpty() {
        when(kpiConfigRepository.findEffectiveConfig(anyLong(), anyLong()))
                .thenReturn(Collections.emptyList());
    }

    // ==================== KPI Configuration ====================

    @Nested
    @DisplayName("KPI Configuration")
    class KPIConfigTests {

        @Test
        @DisplayName("1. getKPIConfigs returns configs for restaurant")
        void getKPIConfigs_returnsConfigsForRestaurant() {
            WaiterKPIConfig config1 = WaiterKPIConfig.builder()
                    .id(1L).name("Default KPI").targetOrdersPerDay(20).build();
            WaiterKPIConfig config2 = WaiterKPIConfig.builder()
                    .id(2L).name("Peak Hours KPI").targetOrdersPerDay(30).waiter(waiter).build();

            when(kpiConfigRepository.findByRestaurantIdOrderByCreatedAtDesc(RESTAURANT_ID))
                    .thenReturn(List.of(config1, config2));

            List<WaiterKPIConfig> result = performanceService.getKPIConfigs(RESTAURANT_ID);

            assertEquals(2, result.size());
            assertEquals("Default KPI", result.get(0).getName());
            assertEquals("Peak Hours KPI", result.get(1).getName());
            verify(kpiConfigRepository).findByRestaurantIdOrderByCreatedAtDesc(RESTAURANT_ID);
        }

        @Test
        @DisplayName("2. getEffectiveKPIConfig returns waiter-specific config when available")
        void getEffectiveKPIConfig_waiterSpecific_returnsWaiterConfig() {
            WaiterKPIConfig waiterConfig = WaiterKPIConfig.builder()
                    .id(1L).waiter(waiter).targetOrdersPerDay(30)
                    .targetRevenuePerDay(BigDecimal.valueOf(800)).build();
            WaiterKPIConfig restaurantDefault = WaiterKPIConfig.builder()
                    .id(2L).targetOrdersPerDay(20)
                    .targetRevenuePerDay(BigDecimal.valueOf(500)).build();

            when(kpiConfigRepository.findEffectiveConfig(WAITER_ID, RESTAURANT_ID))
                    .thenReturn(List.of(waiterConfig, restaurantDefault));

            WaiterKPIConfig result = performanceService.getEffectiveKPIConfig(WAITER_ID, RESTAURANT_ID);

            assertEquals(1L, result.getId());
            assertEquals(30, result.getTargetOrdersPerDay());
            assertEquals(0, BigDecimal.valueOf(800).compareTo(result.getTargetRevenuePerDay()));
            assertNotNull(result.getWaiter());
            assertEquals(WAITER_ID, result.getWaiter().getId());
        }

        @Test
        @DisplayName("3. getEffectiveKPIConfig returns default when no config exists")
        void getEffectiveKPIConfig_noConfig_returnsDefault() {
            when(kpiConfigRepository.findEffectiveConfig(WAITER_ID, RESTAURANT_ID))
                    .thenReturn(Collections.emptyList());

            WaiterKPIConfig result = performanceService.getEffectiveKPIConfig(WAITER_ID, RESTAURANT_ID);

            assertNotNull(result);
            assertNull(result.getId());
            assertEquals(20, result.getTargetOrdersPerDay());
            assertEquals(0, BigDecimal.valueOf(500).compareTo(result.getTargetRevenuePerDay()));
            assertEquals(0, BigDecimal.valueOf(25).compareTo(result.getTargetAvgTicket()));
            assertEquals(10, result.getTargetTablesPerShift());
            assertEquals(45, result.getTargetAvgServiceTimeMinutes());
            assertEquals(0, BigDecimal.valueOf(2.0).compareTo(result.getMaxComplaintRatePercent()));
            assertEquals(0, BigDecimal.valueOf(4.0).compareTo(result.getMinCustomerRating()));
            assertEquals(0, BigDecimal.valueOf(15.0).compareTo(result.getTargetUpsellRatePercent()));
            assertEquals(0, BigDecimal.valueOf(20.0).compareTo(result.getTargetDessertAttachRatePercent()));
            assertEquals(0, BigDecimal.valueOf(60.0).compareTo(result.getTargetBeverageAttachRatePercent()));
            assertEquals(0, BigDecimal.valueOf(100.0).compareTo(result.getBonusThresholdPercent()));
            assertEquals(0, BigDecimal.valueOf(50.0).compareTo(result.getBonusAmountPerThreshold()));
        }

        @Test
        @DisplayName("4. saveKPIConfig creates new config")
        void saveKPIConfig_newConfig_creates() {
            WaiterKPIConfigRequest request = WaiterKPIConfigRequest.builder()
                    .name("New KPI Config")
                    .targetOrdersPerDay(25)
                    .targetRevenuePerDay(BigDecimal.valueOf(600))
                    .targetAvgTicket(BigDecimal.valueOf(30))
                    .targetTablesPerShift(12)
                    .targetAvgServiceTimeMinutes(40)
                    .maxComplaintRatePercent(BigDecimal.valueOf(1.5))
                    .minCustomerRating(BigDecimal.valueOf(4.5))
                    .targetUpsellRatePercent(BigDecimal.valueOf(20.0))
                    .targetDessertAttachRatePercent(BigDecimal.valueOf(25.0))
                    .targetBeverageAttachRatePercent(BigDecimal.valueOf(65.0))
                    .bonusThresholdPercent(BigDecimal.valueOf(110.0))
                    .bonusAmountPerThreshold(BigDecimal.valueOf(75.0))
                    .active(true)
                    .build();

            when(restaurantRepository.findById(RESTAURANT_ID)).thenReturn(Optional.of(restaurant));
            when(kpiConfigRepository.save(any(WaiterKPIConfig.class)))
                    .thenAnswer(invocation -> {
                        WaiterKPIConfig saved = invocation.getArgument(0);
                        saved.setId(10L);
                        return saved;
                    });

            WaiterKPIConfig result = performanceService.saveKPIConfig(RESTAURANT_ID, request);

            verify(kpiConfigRepository).save(kpiConfigCaptor.capture());
            WaiterKPIConfig captured = kpiConfigCaptor.getValue();

            assertEquals("New KPI Config", captured.getName());
            assertEquals(25, captured.getTargetOrdersPerDay());
            assertEquals(0, BigDecimal.valueOf(600).compareTo(captured.getTargetRevenuePerDay()));
            assertEquals(0, BigDecimal.valueOf(30).compareTo(captured.getTargetAvgTicket()));
            assertEquals(12, captured.getTargetTablesPerShift());
            assertEquals(40, captured.getTargetAvgServiceTimeMinutes());
            assertEquals(0, BigDecimal.valueOf(1.5).compareTo(captured.getMaxComplaintRatePercent()));
            assertEquals(0, BigDecimal.valueOf(4.5).compareTo(captured.getMinCustomerRating()));
            assertEquals(0, BigDecimal.valueOf(20.0).compareTo(captured.getTargetUpsellRatePercent()));
            assertEquals(0, BigDecimal.valueOf(25.0).compareTo(captured.getTargetDessertAttachRatePercent()));
            assertEquals(0, BigDecimal.valueOf(65.0).compareTo(captured.getTargetBeverageAttachRatePercent()));
            assertEquals(0, BigDecimal.valueOf(110.0).compareTo(captured.getBonusThresholdPercent()));
            assertEquals(0, BigDecimal.valueOf(75.0).compareTo(captured.getBonusAmountPerThreshold()));
            assertTrue(captured.getActive());
            assertNull(captured.getWaiter());
            assertEquals(restaurant, captured.getRestaurant());
        }

        @Test
        @DisplayName("5. deleteKPIConfig delegates to repository")
        void deleteKPIConfig_delegatesToRepository() {
            Long configId = 42L;

            performanceService.deleteKPIConfig(configId);

            verify(kpiConfigRepository).deleteById(configId);
        }
    }

    // ==================== Performance Recording ====================

    @Nested
    @DisplayName("Performance Recording")
    class PerformanceRecordingTests {

        @Test
        @DisplayName("6. recordOrderCompletion creates new performance record")
        void recordOrderCompletion_createsNewPerformance() {
            Order order = createOrder(1L, OrderStatus.COMPLETED);
            order.setTotal(BigDecimal.valueOf(50));
            order.setGrandTotal(BigDecimal.valueOf(55));

            stubNewPerformanceCreation();
            stubKPIConfigEmpty();

            performanceService.recordOrderCompletion(order, WAITER_ID, RESTAURANT_ID);

            verify(performanceRepository, atLeast(1)).save(performanceCaptor.capture());
            WaiterPerformance lastSaved = performanceCaptor.getAllValues()
                    .get(performanceCaptor.getAllValues().size() - 1);

            assertEquals(1, lastSaved.getTotalOrders());
            assertEquals(0, BigDecimal.valueOf(55).compareTo(lastSaved.getTotalRevenue()));
            BigDecimal expectedAvg = BigDecimal.valueOf(55)
                    .divide(BigDecimal.valueOf(1), 2, RoundingMode.HALF_UP);
            assertEquals(0, expectedAvg.compareTo(lastSaved.getAvgTicketValue()));
        }

        @Test
        @DisplayName("7. recordOrderCompletion updates existing performance (increments totalOrders, adds revenue)")
        void recordOrderCompletion_updatesExistingPerformance() {
            WaiterPerformance existing = createPerformanceWithOrders(2, BigDecimal.valueOf(60000));

            Order order = createOrder(1L, OrderStatus.COMPLETED);
            order.setTotal(BigDecimal.valueOf(30000));
            order.setGrandTotal(BigDecimal.valueOf(30000));

            stubExistingPerformance(existing);
            stubKPIConfigEmpty();

            performanceService.recordOrderCompletion(order, WAITER_ID, RESTAURANT_ID);

            verify(performanceRepository).save(performanceCaptor.capture());
            WaiterPerformance saved = performanceCaptor.getValue();

            assertEquals(3, saved.getTotalOrders());
            assertEquals(0, BigDecimal.valueOf(90000).compareTo(saved.getTotalRevenue()));
        }

        @Test
        @DisplayName("8. recordOrderCompletion calculates average ticket correctly")
        void recordOrderCompletion_calculatesAverageTicket() {
            WaiterPerformance existing = createPerformanceWithOrders(3, BigDecimal.valueOf(90));

            Order order = createOrder(1L, OrderStatus.COMPLETED);
            order.setTotal(BigDecimal.valueOf(60));
            order.setGrandTotal(BigDecimal.valueOf(60));

            stubExistingPerformance(existing);
            stubKPIConfigEmpty();

            performanceService.recordOrderCompletion(order, WAITER_ID, RESTAURANT_ID);

            verify(performanceRepository).save(performanceCaptor.capture());
            WaiterPerformance saved = performanceCaptor.getValue();

            assertEquals(4, saved.getTotalOrders());
            assertEquals(0, BigDecimal.valueOf(150).compareTo(saved.getTotalRevenue()));
            // avgTicket = 150 / 4 = 37.50
            BigDecimal expectedAvg = BigDecimal.valueOf(150)
                    .divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);
            assertEquals(0, expectedAvg.compareTo(saved.getAvgTicketValue()));
        }

        @Test
        @DisplayName("9. recordTableServed increments counters")
        void recordTableServed_incrementsCounters() {
            WaiterPerformance existing = createPerformance(WAITER_ID, LocalDate.now());
            existing.setTotalTablesServed(2);
            existing.setTotalCustomersServed(6);
            stubExistingPerformance(existing);

            performanceService.recordTableServed(WAITER_ID, RESTAURANT_ID, 4);

            verify(performanceRepository).save(performanceCaptor.capture());
            WaiterPerformance saved = performanceCaptor.getValue();
            assertEquals(3, saved.getTotalTablesServed());
            assertEquals(10, saved.getTotalCustomersServed());
        }

        @Test
        @DisplayName("10. recordTip adds tip amount")
        void recordTip_addsTipAmount() {
            WaiterPerformance existing = createPerformance(WAITER_ID, LocalDate.now());
            existing.setTotalTips(BigDecimal.valueOf(5000));
            stubExistingPerformance(existing);

            performanceService.recordTip(WAITER_ID, RESTAURANT_ID, BigDecimal.valueOf(3000));

            verify(performanceRepository).save(performanceCaptor.capture());
            WaiterPerformance saved = performanceCaptor.getValue();
            assertEquals(0, BigDecimal.valueOf(8000).compareTo(saved.getTotalTips()));
        }

        @Test
        @DisplayName("11. recordComplaint increments count")
        void recordComplaint_incrementsCount() {
            WaiterPerformance existing = createPerformance(WAITER_ID, LocalDate.now());
            existing.setComplaintsCount(1);
            stubExistingPerformance(existing);
            stubKPIConfigEmpty();

            performanceService.recordComplaint(WAITER_ID, RESTAURANT_ID);

            verify(performanceRepository).save(performanceCaptor.capture());
            WaiterPerformance saved = performanceCaptor.getValue();
            assertEquals(2, saved.getComplaintsCount());
        }

        @Test
        @DisplayName("12. recordCompliment increments count")
        void recordCompliment_incrementsCount() {
            WaiterPerformance existing = createPerformance(WAITER_ID, LocalDate.now());
            existing.setComplimentsCount(3);
            stubExistingPerformance(existing);
            stubKPIConfigEmpty();

            performanceService.recordCompliment(WAITER_ID, RESTAURANT_ID);

            verify(performanceRepository).save(performanceCaptor.capture());
            WaiterPerformance saved = performanceCaptor.getValue();
            assertEquals(4, saved.getComplimentsCount());
        }

        @Test
        @DisplayName("13. recordRating updates running average")
        void recordRating_updatesRunningAverage() {
            WaiterPerformance existing = createPerformance(WAITER_ID, LocalDate.now());
            existing.setAvgCustomerRating(BigDecimal.valueOf(4.00));
            existing.setRatingsCount(2);
            stubExistingPerformance(existing);
            stubKPIConfigEmpty();

            performanceService.recordRating(WAITER_ID, RESTAURANT_ID, BigDecimal.valueOf(5.00));

            verify(performanceRepository).save(performanceCaptor.capture());
            WaiterPerformance saved = performanceCaptor.getValue();
            assertEquals(3, saved.getRatingsCount());
            // Running average: (4.00 * 2 + 5.00) / 3 = 13.00 / 3 = 4.33
            BigDecimal expectedAvg = BigDecimal.valueOf(4.00)
                    .multiply(BigDecimal.valueOf(2))
                    .add(BigDecimal.valueOf(5.00))
                    .divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
            assertEquals(0, expectedAvg.compareTo(saved.getAvgCustomerRating()));
        }

        @Test
        @DisplayName("14. recordVoidItem increments count and value")
        void recordVoidItem_incrementsCountAndValue() {
            WaiterPerformance existing = createPerformance(WAITER_ID, LocalDate.now());
            existing.setVoidItemsCount(1);
            existing.setVoidItemsValue(BigDecimal.valueOf(10));
            stubExistingPerformance(existing);

            performanceService.recordVoidItem(WAITER_ID, RESTAURANT_ID, BigDecimal.valueOf(25));

            verify(performanceRepository).save(performanceCaptor.capture());
            WaiterPerformance saved = performanceCaptor.getValue();
            assertEquals(2, saved.getVoidItemsCount());
            assertEquals(0, BigDecimal.valueOf(35).compareTo(saved.getVoidItemsValue()));
        }
    }

    // ==================== Performance Summary ====================

    @Nested
    @DisplayName("Performance Summary")
    class PerformanceSummaryTests {

        @Test
        @DisplayName("15. getPerformanceSummary aggregates multiple days")
        void getPerformanceSummary_aggregatesMultipleDays() {
            LocalDate startDate = LocalDate.of(2026, 3, 1);
            LocalDate endDate = LocalDate.of(2026, 3, 3);

            WaiterPerformance day1 = createPerformanceWithOrders(10, BigDecimal.valueOf(250));
            day1.setPerformanceDate(LocalDate.of(2026, 3, 1));
            day1.setTotalTips(BigDecimal.valueOf(20));
            day1.setKpiScore(BigDecimal.valueOf(85.00));
            day1.setBonusEarned(BigDecimal.valueOf(10));
            day1.setComplaintsCount(1);
            day1.setComplimentsCount(3);
            day1.setAvgCustomerRating(BigDecimal.valueOf(4.50));

            WaiterPerformance day2 = createPerformanceWithOrders(15, BigDecimal.valueOf(375));
            day2.setId(2L);
            day2.setPerformanceDate(LocalDate.of(2026, 3, 2));
            day2.setTotalTips(BigDecimal.valueOf(30));
            day2.setKpiScore(BigDecimal.valueOf(95.00));
            day2.setBonusEarned(BigDecimal.valueOf(20));
            day2.setComplaintsCount(0);
            day2.setComplimentsCount(5);
            day2.setAvgCustomerRating(BigDecimal.valueOf(4.80));

            WaiterPerformance day3 = createPerformanceWithOrders(8, BigDecimal.valueOf(200));
            day3.setId(3L);
            day3.setPerformanceDate(LocalDate.of(2026, 3, 3));
            day3.setTotalTips(BigDecimal.valueOf(15));
            day3.setKpiScore(BigDecimal.valueOf(70.00));
            day3.setBonusEarned(BigDecimal.valueOf(5));
            day3.setComplaintsCount(2);
            day3.setComplimentsCount(1);
            day3.setAvgCustomerRating(null); // no ratings this day

            List<WaiterPerformance> performances = List.of(day1, day2, day3);

            when(performanceRepository.findByWaiterIdAndPerformanceDateBetweenOrderByPerformanceDateDesc(
                    WAITER_ID, startDate, endDate)).thenReturn(performances);
            when(waiterRepository.findById(WAITER_ID)).thenReturn(Optional.of(waiter));
            when(commissionRepository.getTotalCommissionByWaiterAndDateRange(
                    eq(WAITER_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(BigDecimal.valueOf(100));

            WaiterPerformanceSummary summary = performanceService.getPerformanceSummary(
                    WAITER_ID, startDate, endDate);

            assertEquals(WAITER_ID, summary.getWaiterId());
            assertEquals(startDate, summary.getStartDate());
            assertEquals(endDate, summary.getEndDate());

            // totalOrders: 10 + 15 + 8 = 33
            assertEquals(33, summary.getTotalOrders());

            // totalRevenue: 250 + 375 + 200 = 825
            assertEquals(0, BigDecimal.valueOf(825).compareTo(summary.getTotalRevenue()));

            // totalTips: 20 + 30 + 15 = 65
            assertEquals(0, BigDecimal.valueOf(65).compareTo(summary.getTotalTips()));

            // avgTicket: 825 / 33 = 25.00
            BigDecimal expectedAvgTicket = BigDecimal.valueOf(825)
                    .divide(BigDecimal.valueOf(33), 2, RoundingMode.HALF_UP);
            assertEquals(0, expectedAvgTicket.compareTo(summary.getAvgTicketValue()));

            // avgKpi: (85 + 95 + 70) / 3 = 83.33
            BigDecimal expectedAvgKpi = BigDecimal.valueOf(250)
                    .divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
            assertEquals(0, expectedAvgKpi.compareTo(summary.getAvgKpiScore()));

            // totalBonus: 10 + 20 + 5 = 35
            assertEquals(0, BigDecimal.valueOf(35).compareTo(summary.getTotalBonusEarned()));

            // workingDays = 3
            assertEquals(3, summary.getWorkingDays());

            // complaints: 1 + 0 + 2 = 3
            assertEquals(3, summary.getComplaintsCount());

            // compliments: 3 + 5 + 1 = 9
            assertEquals(9, summary.getComplimentsCount());

            // avgCustomerRating: only day1 (4.50) and day2 (4.80) have ratings
            // (4.50 + 4.80) / 2 = 4.65
            BigDecimal expectedAvgRating = BigDecimal.valueOf(4.50).add(BigDecimal.valueOf(4.80))
                    .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            assertNotNull(summary.getAvgCustomerRating());
            assertEquals(0, expectedAvgRating.compareTo(summary.getAvgCustomerRating()));

            // commission
            assertEquals(0, BigDecimal.valueOf(100).compareTo(summary.getTotalCommission()));

            // dailyPerformances
            assertEquals(3, summary.getDailyPerformances().size());
        }

        @Test
        @DisplayName("16. getPerformanceSummary with empty performances returns zeros")
        void getPerformanceSummary_emptyPerformances_returnsZeros() {
            LocalDate startDate = LocalDate.of(2026, 3, 1);
            LocalDate endDate = LocalDate.of(2026, 3, 7);

            when(performanceRepository.findByWaiterIdAndPerformanceDateBetweenOrderByPerformanceDateDesc(
                    WAITER_ID, startDate, endDate)).thenReturn(Collections.emptyList());
            when(waiterRepository.findById(WAITER_ID)).thenReturn(Optional.of(waiter));
            when(commissionRepository.getTotalCommissionByWaiterAndDateRange(
                    eq(WAITER_ID), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(BigDecimal.ZERO);

            WaiterPerformanceSummary summary = performanceService.getPerformanceSummary(
                    WAITER_ID, startDate, endDate);

            assertEquals(WAITER_ID, summary.getWaiterId());
            assertEquals(startDate, summary.getStartDate());
            assertEquals(endDate, summary.getEndDate());
            assertEquals(0, summary.getTotalOrders());
            assertEquals(0, BigDecimal.ZERO.compareTo(summary.getTotalRevenue()));
            assertEquals(0, BigDecimal.ZERO.compareTo(summary.getTotalTips()));
            assertEquals(0, BigDecimal.ZERO.compareTo(summary.getAvgTicketValue()));
            assertEquals(0, BigDecimal.ZERO.compareTo(summary.getAvgKpiScore()));
            assertEquals(0, BigDecimal.ZERO.compareTo(summary.getTotalBonusEarned()));
            assertEquals(0, summary.getWorkingDays());
            assertEquals(0, summary.getComplaintsCount());
            assertEquals(0, summary.getComplimentsCount());
            assertEquals(0, BigDecimal.ZERO.compareTo(summary.getTotalCommission()));
            assertNull(summary.getDailyPerformances());
        }

        @Test
        @DisplayName("17. getLeaderboard sorts by KPI score descending")
        void getLeaderboard_sortsByKPIScore() {
            LocalDate startDate = LocalDate.of(2026, 3, 1);
            LocalDate endDate = LocalDate.of(2026, 3, 7);

            Waiter waiter1 = createWaiter(1L, "Alice", "1111");
            Waiter waiter2 = createWaiter(2L, "Bob", "2222");
            Waiter waiter3 = createWaiter(3L, "Charlie", "3333");

            // Leaderboard rows: waiterId, waiterName, totalRevenue, totalOrders, avgRating, avgKpiScore
            Object[] row1 = new Object[]{1L, "Alice", BigDecimal.valueOf(500), 20,
                    BigDecimal.valueOf(4.5), BigDecimal.valueOf(80.0)};
            Object[] row2 = new Object[]{2L, "Bob", BigDecimal.valueOf(700), 25,
                    BigDecimal.valueOf(4.8), BigDecimal.valueOf(95.0)};
            Object[] row3 = new Object[]{3L, "Charlie", BigDecimal.valueOf(300), 12,
                    BigDecimal.valueOf(4.0), BigDecimal.valueOf(60.0)};

            when(performanceRepository.getLeaderboard(RESTAURANT_ID, startDate, endDate))
                    .thenReturn(List.of(row1, row2, row3));

            when(commissionRepository.getTotalCommissionByWaiterAndDateRange(
                    eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(BigDecimal.valueOf(50));
            when(commissionRepository.getTotalCommissionByWaiterAndDateRange(
                    eq(2L), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(BigDecimal.valueOf(70));
            when(commissionRepository.getTotalCommissionByWaiterAndDateRange(
                    eq(3L), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(BigDecimal.valueOf(30));

            when(waiterRepository.findById(1L)).thenReturn(Optional.of(waiter1));
            when(waiterRepository.findById(2L)).thenReturn(Optional.of(waiter2));
            when(waiterRepository.findById(3L)).thenReturn(Optional.of(waiter3));

            List<WaiterLeaderboardEntry> leaderboard = performanceService.getLeaderboard(
                    RESTAURANT_ID, startDate, endDate);

            assertEquals(3, leaderboard.size());

            // Sorted by avgKpiScore descending: Bob (95), Alice (80), Charlie (60)
            assertEquals("Bob", leaderboard.get(0).getWaiterName());
            assertEquals(0, BigDecimal.valueOf(95.0).compareTo(leaderboard.get(0).getAvgKpiScore()));
            assertEquals(0, BigDecimal.valueOf(700).compareTo(leaderboard.get(0).getTotalRevenue()));
            assertEquals(25, leaderboard.get(0).getTotalOrders());
            assertEquals(0, BigDecimal.valueOf(70).compareTo(leaderboard.get(0).getTotalCommission()));

            assertEquals("Alice", leaderboard.get(1).getWaiterName());
            assertEquals(0, BigDecimal.valueOf(80.0).compareTo(leaderboard.get(1).getAvgKpiScore()));
            assertEquals(0, BigDecimal.valueOf(500).compareTo(leaderboard.get(1).getTotalRevenue()));
            assertEquals(20, leaderboard.get(1).getTotalOrders());
            assertEquals(0, BigDecimal.valueOf(50).compareTo(leaderboard.get(1).getTotalCommission()));

            assertEquals("Charlie", leaderboard.get(2).getWaiterName());
            assertEquals(0, BigDecimal.valueOf(60.0).compareTo(leaderboard.get(2).getAvgKpiScore()));
            assertEquals(0, BigDecimal.valueOf(300).compareTo(leaderboard.get(2).getTotalRevenue()));
            assertEquals(12, leaderboard.get(2).getTotalOrders());
            assertEquals(0, BigDecimal.valueOf(30).compareTo(leaderboard.get(2).getTotalCommission()));
        }
    }
}
