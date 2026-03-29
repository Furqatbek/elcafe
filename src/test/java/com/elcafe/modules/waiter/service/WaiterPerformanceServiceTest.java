package com.elcafe.modules.waiter.service;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.entity.WaiterKPIConfig;
import com.elcafe.modules.waiter.entity.WaiterPerformance;
import com.elcafe.modules.waiter.repository.WaiterCommissionRepository;
import com.elcafe.modules.waiter.repository.WaiterKPIConfigRepository;
import com.elcafe.modules.waiter.repository.WaiterPerformanceRepository;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createRestaurant;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createWaiter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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

    private Waiter waiter;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        waiter = createWaiter();
        restaurant = createRestaurant();
    }

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

    // ==================== KPI Config ====================

    @Test
    @DisplayName("getKPIConfigs returns configs for restaurant")
    void getKPIConfigs_returnsConfigs() {
        WaiterKPIConfig config = WaiterKPIConfig.builder().id(1L).targetOrdersPerDay(20).build();
        when(kpiConfigRepository.findByRestaurantIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(config));

        List<WaiterKPIConfig> result = performanceService.getKPIConfigs(1L);
        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("getEffectiveKPIConfig returns waiter-specific config if exists")
    void getEffectiveKPIConfig_waiterSpecific() {
        WaiterKPIConfig config = WaiterKPIConfig.builder().id(1L).waiter(waiter).targetOrdersPerDay(30).build();
        when(kpiConfigRepository.findEffectiveConfig(1L, 1L)).thenReturn(List.of(config));

        WaiterKPIConfig result = performanceService.getEffectiveKPIConfig(1L, 1L);
        assertEquals(30, result.getTargetOrdersPerDay());
    }

    @Test
    @DisplayName("getEffectiveKPIConfig returns default when no config exists")
    void getEffectiveKPIConfig_returnsDefault() {
        when(kpiConfigRepository.findEffectiveConfig(1L, 1L)).thenReturn(List.of());

        WaiterKPIConfig result = performanceService.getEffectiveKPIConfig(1L, 1L);
        assertNotNull(result);
        assertEquals(20, result.getTargetOrdersPerDay()); // default value
    }

    @Test
    @DisplayName("deleteKPIConfig delegates to repository")
    void deleteKPIConfig_delegates() {
        performanceService.deleteKPIConfig(1L);
        verify(kpiConfigRepository).deleteById(1L);
    }

    // ==================== Performance Recording ====================

    @Test
    @DisplayName("recordOrderCompletion creates new performance and updates metrics")
    void recordOrderCompletion_createsAndUpdates() {
        Order order = createOrder(1L, OrderStatus.COMPLETED);
        order.setTotal(BigDecimal.valueOf(50000));
        order.setGrandTotal(BigDecimal.valueOf(50000));

        WaiterPerformance perf = createPerformance(1L, LocalDate.now());
        when(performanceRepository.findByWaiterIdAndPerformanceDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(perf));
        when(kpiConfigRepository.findEffectiveConfig(anyLong(), anyLong())).thenReturn(List.of());
        when(performanceRepository.save(any(WaiterPerformance.class))).thenAnswer(i -> i.getArgument(0));

        performanceService.recordOrderCompletion(order, 1L, 1L);

        ArgumentCaptor<WaiterPerformance> captor = ArgumentCaptor.forClass(WaiterPerformance.class);
        verify(performanceRepository).save(captor.capture());

        WaiterPerformance saved = captor.getValue();
        assertEquals(1, saved.getTotalOrders());
        assertEquals(0, BigDecimal.valueOf(50000).compareTo(saved.getTotalRevenue()));
        assertEquals(0, BigDecimal.valueOf(50000).compareTo(saved.getAvgTicketValue()));
    }

    @Test
    @DisplayName("recordOrderCompletion updates existing performance")
    void recordOrderCompletion_updatesExisting() {
        Order order = createOrder(1L, OrderStatus.COMPLETED);
        order.setTotal(BigDecimal.valueOf(30000));
        order.setGrandTotal(BigDecimal.valueOf(30000));

        WaiterPerformance perf = createPerformance(1L, LocalDate.now());
        perf.setTotalOrders(2);
        perf.setTotalRevenue(BigDecimal.valueOf(60000));

        when(performanceRepository.findByWaiterIdAndPerformanceDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(perf));
        when(kpiConfigRepository.findEffectiveConfig(anyLong(), anyLong())).thenReturn(List.of());
        when(performanceRepository.save(any(WaiterPerformance.class))).thenAnswer(i -> i.getArgument(0));

        performanceService.recordOrderCompletion(order, 1L, 1L);

        ArgumentCaptor<WaiterPerformance> captor = ArgumentCaptor.forClass(WaiterPerformance.class);
        verify(performanceRepository).save(captor.capture());

        WaiterPerformance saved = captor.getValue();
        assertEquals(3, saved.getTotalOrders());
        assertEquals(0, BigDecimal.valueOf(90000).compareTo(saved.getTotalRevenue()));
        // avg = 90000/3 = 30000
        assertEquals(0, BigDecimal.valueOf(30000).compareTo(saved.getAvgTicketValue()));
    }

    @Test
    @DisplayName("recordTableServed increments counters")
    void recordTableServed_increments() {
        WaiterPerformance perf = createPerformance(1L, LocalDate.now());
        when(performanceRepository.findByWaiterIdAndPerformanceDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(perf));
        when(performanceRepository.save(any(WaiterPerformance.class))).thenAnswer(i -> i.getArgument(0));

        performanceService.recordTableServed(1L, 1L, 4);

        ArgumentCaptor<WaiterPerformance> captor = ArgumentCaptor.forClass(WaiterPerformance.class);
        verify(performanceRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getTotalTablesServed());
        assertEquals(4, captor.getValue().getTotalCustomersServed());
    }

    @Test
    @DisplayName("recordTip adds tip amount")
    void recordTip_addsTip() {
        WaiterPerformance perf = createPerformance(1L, LocalDate.now());
        perf.setTotalTips(BigDecimal.valueOf(5000));
        when(performanceRepository.findByWaiterIdAndPerformanceDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(perf));
        when(performanceRepository.save(any(WaiterPerformance.class))).thenAnswer(i -> i.getArgument(0));

        performanceService.recordTip(1L, 1L, BigDecimal.valueOf(3000));

        ArgumentCaptor<WaiterPerformance> captor = ArgumentCaptor.forClass(WaiterPerformance.class);
        verify(performanceRepository).save(captor.capture());
        assertEquals(0, BigDecimal.valueOf(8000).compareTo(captor.getValue().getTotalTips()));
    }

    @Test
    @DisplayName("recordComplaint increments count")
    void recordComplaint_increments() {
        WaiterPerformance perf = createPerformance(1L, LocalDate.now());
        when(performanceRepository.findByWaiterIdAndPerformanceDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(perf));
        when(kpiConfigRepository.findEffectiveConfig(anyLong(), anyLong())).thenReturn(List.of());
        when(performanceRepository.save(any(WaiterPerformance.class))).thenAnswer(i -> i.getArgument(0));

        performanceService.recordComplaint(1L, 1L);

        ArgumentCaptor<WaiterPerformance> captor = ArgumentCaptor.forClass(WaiterPerformance.class);
        verify(performanceRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getComplaintsCount());
    }

    @Test
    @DisplayName("recordCompliment increments count")
    void recordCompliment_increments() {
        WaiterPerformance perf = createPerformance(1L, LocalDate.now());
        when(performanceRepository.findByWaiterIdAndPerformanceDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(perf));
        when(kpiConfigRepository.findEffectiveConfig(anyLong(), anyLong())).thenReturn(List.of());
        when(performanceRepository.save(any(WaiterPerformance.class))).thenAnswer(i -> i.getArgument(0));

        performanceService.recordCompliment(1L, 1L);

        ArgumentCaptor<WaiterPerformance> captor = ArgumentCaptor.forClass(WaiterPerformance.class);
        verify(performanceRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getComplimentsCount());
    }

    @Test
    @DisplayName("recordVoidItem increments count and value")
    void recordVoidItem_incrementsCountAndValue() {
        WaiterPerformance perf = createPerformance(1L, LocalDate.now());
        when(performanceRepository.findByWaiterIdAndPerformanceDate(eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(perf));
        when(performanceRepository.save(any(WaiterPerformance.class))).thenAnswer(i -> i.getArgument(0));

        performanceService.recordVoidItem(1L, 1L, BigDecimal.valueOf(15000));

        ArgumentCaptor<WaiterPerformance> captor = ArgumentCaptor.forClass(WaiterPerformance.class);
        verify(performanceRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getVoidItemsCount());
        assertEquals(0, BigDecimal.valueOf(15000).compareTo(captor.getValue().getVoidItemsValue()));
    }
}
