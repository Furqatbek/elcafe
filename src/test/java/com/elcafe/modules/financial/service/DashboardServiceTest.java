package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.dto.DashboardResponse;
import com.elcafe.modules.financial.repository.ExpenseRepository;
import com.elcafe.modules.financial.repository.PayrollEntryRepository;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrderItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private PayrollEntryRepository payrollRepository;
    @Mock private InventoryIngredientRepository ingredientRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ShiftTimeService shiftTimeService;

    @InjectMocks private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        // Default shift range
        ShiftTimeService.ShiftTimeRange range = new ShiftTimeService.ShiftTimeRange(
                OffsetDateTime.of(LocalDate.now(), LocalTime.of(9, 0), ZoneOffset.of("+05:00")),
                OffsetDateTime.of(LocalDate.now(), LocalTime.of(23, 0), ZoneOffset.of("+05:00"))
        );
        when(shiftTimeService.getShiftTimeRange(anyLong(), any(LocalDate.class))).thenReturn(range);
        when(shiftTimeService.getShiftTimeRangeForPeriod(anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(range);
    }

    private Order createCompletedOrder(BigDecimal total) {
        Order order = createOrder(1L, OrderStatus.COMPLETED);
        order.setTotal(total);
        order.setSubtotal(total);
        order.getItems().add(createOrderItem(1L, 1L, "Item", 1, total));
        return order;
    }

    @Test
    @DisplayName("getDashboard — calculates revenue from orders")
    void getDashboard_calculatesRevenue() {
        List<Order> orders = List.of(
                createCompletedOrder(BigDecimal.valueOf(50000)),
                createCompletedOrder(BigDecimal.valueOf(30000))
        );
        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(
                anyLong(), any(), any())).thenReturn(orders);
        when(expenseRepository.sumAmountByRestaurantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(payrollRepository.sumAmountByRestaurantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(shiftTimeService.getRevenueStatusList()).thenReturn(List.of(OrderStatus.COMPLETED, OrderStatus.DELIVERED));

        DashboardResponse result = dashboardService.getDashboard(1L, LocalDate.now(), LocalDate.now());

        assertNotNull(result);
        assertEquals(0, BigDecimal.valueOf(80000).compareTo(result.getTotalIncome()));
    }

    @Test
    @DisplayName("getDashboard — empty orders returns zeros")
    void getDashboard_emptyOrders_returnsZeros() {
        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(
                anyLong(), any(), any())).thenReturn(List.of());
        when(expenseRepository.sumAmountByRestaurantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(null);
        when(payrollRepository.sumAmountByRestaurantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(null);
        when(shiftTimeService.getRevenueStatusList()).thenReturn(List.of(OrderStatus.COMPLETED));

        DashboardResponse result = dashboardService.getDashboard(1L, LocalDate.now(), LocalDate.now());

        assertNotNull(result);
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getTotalIncome()));
    }

    @Test
    @DisplayName("getDashboard — includes expenses in calculation")
    void getDashboard_includesExpenses() {
        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(
                anyLong(), any(), any())).thenReturn(List.of(createCompletedOrder(BigDecimal.valueOf(100000))));
        when(expenseRepository.sumAmountByRestaurantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(BigDecimal.valueOf(30000));
        when(payrollRepository.sumAmountByRestaurantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(shiftTimeService.getRevenueStatusList()).thenReturn(List.of(OrderStatus.COMPLETED));

        DashboardResponse result = dashboardService.getDashboard(1L, LocalDate.now(), LocalDate.now());

        assertEquals(0, BigDecimal.valueOf(30000).compareTo(result.getTotalExpenses()));
    }

    @Test
    @DisplayName("getDashboard — includes payroll in calculation")
    void getDashboard_includesPayroll() {
        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(
                anyLong(), any(), any())).thenReturn(List.of(createCompletedOrder(BigDecimal.valueOf(100000))));
        when(expenseRepository.sumAmountByRestaurantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(payrollRepository.sumAmountByRestaurantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(BigDecimal.valueOf(20000));
        when(shiftTimeService.getRevenueStatusList()).thenReturn(List.of(OrderStatus.COMPLETED));

        DashboardResponse result = dashboardService.getDashboard(1L, LocalDate.now(), LocalDate.now());

        assertEquals(0, BigDecimal.valueOf(20000).compareTo(result.getTotalPayroll()));
    }

    @Test
    @DisplayName("getTodaySummary — delegates with today's date")
    void getTodaySummary_delegates() {
        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(
                anyLong(), any(), any())).thenReturn(List.of());
        when(expenseRepository.sumAmountByRestaurantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(null);
        when(payrollRepository.sumAmountByRestaurantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(null);
        when(shiftTimeService.getRevenueStatusList()).thenReturn(List.of(OrderStatus.COMPLETED));

        DashboardResponse result = dashboardService.getTodaySummary(1L);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getWeekSummary — delegates with 7-day range")
    void getWeekSummary_delegates() {
        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(
                anyLong(), any(), any())).thenReturn(List.of());
        when(expenseRepository.sumAmountByRestaurantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(null);
        when(payrollRepository.sumAmountByRestaurantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(null);
        when(shiftTimeService.getRevenueStatusList()).thenReturn(List.of(OrderStatus.COMPLETED));

        DashboardResponse result = dashboardService.getWeekSummary(1L);
        assertNotNull(result);
    }

    @Test
    @DisplayName("getMonthSummary — delegates with month range")
    void getMonthSummary_delegates() {
        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(
                anyLong(), any(), any())).thenReturn(List.of());
        when(expenseRepository.sumAmountByRestaurantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(null);
        when(payrollRepository.sumAmountByRestaurantIdAndDateRange(anyLong(), any(), any()))
                .thenReturn(null);
        when(shiftTimeService.getRevenueStatusList()).thenReturn(List.of(OrderStatus.COMPLETED));

        DashboardResponse result = dashboardService.getMonthSummary(1L);
        assertNotNull(result);
    }
}
