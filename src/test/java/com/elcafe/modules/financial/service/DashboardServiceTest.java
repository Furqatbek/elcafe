package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.dto.DashboardResponse;
import com.elcafe.modules.financial.entity.Expense;
import com.elcafe.modules.financial.entity.PayrollEntry;
import com.elcafe.modules.financial.repository.ExpenseRepository;
import com.elcafe.modules.financial.repository.PayrollEntryRepository;
import com.elcafe.modules.financial.service.ShiftTimeService.ShiftTimeRange;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private PayrollEntryRepository payrollRepository;

    @Mock
    private InventoryIngredientRepository ingredientRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ShiftTimeService shiftTimeService;

    @InjectMocks
    private DashboardService dashboardService;

    private static final Long RESTAURANT_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 3, 29);
    private static final LocalTime OPEN_TIME = LocalTime.of(10, 0);
    private static final LocalTime CLOSE_TIME = LocalTime.of(23, 0);

    private ShiftTimeRange defaultShiftRange;
    private ShiftTimeRange previousPeriodShiftRange;

    @BeforeEach
    void setUp() {
        ZoneId zone = ZoneId.systemDefault();
        OffsetDateTime shiftStart = TODAY.atTime(OPEN_TIME).atZone(zone).toOffsetDateTime();
        OffsetDateTime shiftEnd = TODAY.atTime(CLOSE_TIME).atZone(zone).toOffsetDateTime();
        defaultShiftRange = new ShiftTimeRange(shiftStart, shiftEnd, OPEN_TIME, CLOSE_TIME);

        LocalDate prevDate = TODAY.minusDays(1);
        OffsetDateTime prevStart = prevDate.atTime(OPEN_TIME).atZone(zone).toOffsetDateTime();
        OffsetDateTime prevEnd = prevDate.atTime(CLOSE_TIME).atZone(zone).toOffsetDateTime();
        previousPeriodShiftRange = new ShiftTimeRange(prevStart, prevEnd, OPEN_TIME, CLOSE_TIME);
    }

    // ---------------------------------------------------------------------------
    // Helper methods
    // ---------------------------------------------------------------------------

    /**
     * Stubs all repository and service mocks for a standard single-day getDashboard call.
     * The first invocation of each repository returns the provided data; the second
     * invocation (used by the previous-period comparison) returns empty lists.
     */
    private void stubDefaultMocks(List<Order> orders,
                                  List<Expense> expenses,
                                  List<PayrollEntry> payroll) {
        when(shiftTimeService.getShiftTimeRangeForPeriod(
                eq(RESTAURANT_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(defaultShiftRange)
                .thenReturn(previousPeriodShiftRange);

        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(
                eq(RESTAURANT_ID), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(orders)
                .thenReturn(Collections.emptyList());

        when(expenseRepository.findByRestaurant_IdAndExpenseDateBetween(
                eq(RESTAURANT_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(expenses)
                .thenReturn(Collections.emptyList());

        when(payrollRepository.findByRestaurant_IdAndPayPeriodEndBetween(
                eq(RESTAURANT_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(payroll)
                .thenReturn(Collections.emptyList());

        when(ingredientRepository.findByRestaurant_IdAndActiveTrue(RESTAURANT_ID))
                .thenReturn(Collections.emptyList());

        when(shiftTimeService.getBusinessDay(eq(RESTAURANT_ID), any()))
                .thenReturn(TODAY);
    }

    private void stubDefaultMocks() {
        stubDefaultMocks(Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private Order createCompletedOrder(Long id, BigDecimal total, List<OrderItem> items) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);

        Order order = Order.builder()
                .orderNumber("ORD-" + id)
                .restaurant(restaurant)
                .status(OrderStatus.COMPLETED)
                .orderType(OrderType.DINE_IN)
                .orderSource(OrderSource.WAITER)
                .paymentStatus(PaymentStatus.COMPLETED)
                .subtotal(total)
                .deliveryFee(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .total(total)
                .items(items != null ? new ArrayList<>(items) : new ArrayList<>())
                .payments(new ArrayList<>())
                .build();
        order.setId(id);
        order.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        order.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return order;
    }

    private Order createCancelledOrder(Long id) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);

        Order order = Order.builder()
                .orderNumber("ORD-C-" + id)
                .restaurant(restaurant)
                .status(OrderStatus.CANCELLED)
                .orderType(OrderType.DINE_IN)
                .orderSource(OrderSource.WAITER)
                .subtotal(BigDecimal.valueOf(50))
                .deliveryFee(BigDecimal.ZERO)
                .tax(BigDecimal.ZERO)
                .discount(BigDecimal.ZERO)
                .total(BigDecimal.valueOf(50))
                .items(new ArrayList<>())
                .payments(new ArrayList<>())
                .build();
        order.setId(id);
        order.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        order.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return order;
    }

    private OrderItem createItem(Long productId, String name, int qty, BigDecimal unitPrice) {
        return OrderItem.builder()
                .id(productId * 100 + qty)
                .productId(productId)
                .productName(name)
                .quantity(qty)
                .unitPrice(unitPrice)
                .totalPrice(unitPrice.multiply(BigDecimal.valueOf(qty)))
                .build();
    }

    private Expense createPaidExpense(Long id, BigDecimal amount) {
        return Expense.builder()
                .id(id)
                .expenseNumber("EXP-" + id)
                .expenseDate(TODAY)
                .category(Expense.ExpenseCategory.SUPPLIES)
                .description("Test expense " + id)
                .amount(amount)
                .totalAmount(amount)
                .paymentStatus(Expense.PaymentStatus.PAID)
                .build();
    }

    private PayrollEntry createPaidPayroll(Long id, BigDecimal netPay) {
        return PayrollEntry.builder()
                .id(id)
                .payrollNumber("PAY-" + id)
                .payPeriodStart(TODAY.minusDays(7))
                .payPeriodEnd(TODAY)
                .payrollType(PayrollEntry.PayrollType.SALARY)
                .netPay(netPay)
                .status(PayrollEntry.PaymentStatus.PAID)
                .build();
    }

    // ---------------------------------------------------------------------------
    // 1. getDashboard_calculatesRevenue
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("getDashboard calculates total revenue by summing completed order totals")
    void getDashboard_calculatesRevenue() {
        Order order1 = createCompletedOrder(1L, BigDecimal.valueOf(100), List.of());
        Order order2 = createCompletedOrder(2L, BigDecimal.valueOf(250), List.of());
        Order order3 = createCompletedOrder(3L, new BigDecimal("75.50"), List.of());

        stubDefaultMocks(List.of(order1, order2, order3), Collections.emptyList(), Collections.emptyList());

        DashboardResponse result = dashboardService.getDashboard(RESTAURANT_ID, TODAY, TODAY);

        // 100 + 250 + 75.50 = 425.50
        assertThat(result.getTotalIncome()).isEqualByComparingTo(new BigDecimal("425.50"));
    }

    // ---------------------------------------------------------------------------
    // 2. getDashboard_calculatesExpenses
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("getDashboard calculates total expenses by summing only paid expense amounts")
    void getDashboard_calculatesExpenses() {
        Expense paid1 = createPaidExpense(1L, BigDecimal.valueOf(200));
        Expense paid2 = createPaidExpense(2L, BigDecimal.valueOf(150));

        Expense unpaid = Expense.builder()
                .id(3L)
                .expenseNumber("EXP-003")
                .expenseDate(TODAY)
                .category(Expense.ExpenseCategory.RENT)
                .description("Unpaid expense")
                .amount(BigDecimal.valueOf(500))
                .totalAmount(BigDecimal.valueOf(500))
                .paymentStatus(Expense.PaymentStatus.UNPAID)
                .build();

        stubDefaultMocks(Collections.emptyList(), List.of(paid1, paid2, unpaid), Collections.emptyList());

        DashboardResponse result = dashboardService.getDashboard(RESTAURANT_ID, TODAY, TODAY);

        // Only paid expenses count: 200 + 150 = 350 (unpaid excluded, no payroll)
        assertThat(result.getTotalExpenses()).isEqualByComparingTo(BigDecimal.valueOf(350));
    }

    // ---------------------------------------------------------------------------
    // 3. getDashboard_calculatesPayroll
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("getDashboard includes paid payroll entries in total expenses")
    void getDashboard_calculatesPayroll() {
        PayrollEntry paid1 = createPaidPayroll(1L, BigDecimal.valueOf(1000));
        PayrollEntry paid2 = createPaidPayroll(2L, BigDecimal.valueOf(800));

        PayrollEntry pending = PayrollEntry.builder()
                .id(3L)
                .payrollNumber("PAY-003")
                .payPeriodStart(TODAY.minusDays(7))
                .payPeriodEnd(TODAY)
                .payrollType(PayrollEntry.PayrollType.SALARY)
                .netPay(BigDecimal.valueOf(500))
                .status(PayrollEntry.PaymentStatus.PENDING)
                .build();

        stubDefaultMocks(Collections.emptyList(), Collections.emptyList(), List.of(paid1, paid2, pending));

        DashboardResponse result = dashboardService.getDashboard(RESTAURANT_ID, TODAY, TODAY);

        // Payroll is added to totalExpenses: 1000 + 800 = 1800 (pending excluded)
        assertThat(result.getTotalExpenses()).isEqualByComparingTo(BigDecimal.valueOf(1800));
    }

    // ---------------------------------------------------------------------------
    // 4. getDashboard_calculatesProfitMargin
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("getDashboard calculates profit margin as (revenue - expenses - payroll) / revenue * 100")
    void getDashboard_calculatesProfitMargin() {
        Order order = createCompletedOrder(1L, BigDecimal.valueOf(1000), List.of());
        Expense expense = createPaidExpense(1L, BigDecimal.valueOf(200));
        PayrollEntry payroll = createPaidPayroll(1L, BigDecimal.valueOf(300));

        stubDefaultMocks(List.of(order), List.of(expense), List.of(payroll));

        DashboardResponse result = dashboardService.getDashboard(RESTAURANT_ID, TODAY, TODAY);

        // totalIncome = 1000
        // totalExpenses = 200 (expense) + 300 (payroll) = 500
        // netProfit = 1000 - 500 = 500
        // profitMargin = (500 / 1000) * 100 = 50.0000
        assertThat(result.getTotalIncome()).isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(result.getTotalExpenses()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(result.getNetProfit()).isEqualByComparingTo(BigDecimal.valueOf(500));

        BigDecimal expectedMargin = BigDecimal.valueOf(500)
                .divide(BigDecimal.valueOf(1000), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        assertThat(result.getProfitMargin()).isEqualByComparingTo(expectedMargin);
    }

    // ---------------------------------------------------------------------------
    // 5. getDashboard_emptyOrders_returnsZeros
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("getDashboard returns zeros when there are no orders, expenses, or payroll")
    void getDashboard_emptyOrders_returnsZeros() {
        stubDefaultMocks();

        DashboardResponse result = dashboardService.getDashboard(RESTAURANT_ID, TODAY, TODAY);

        assertThat(result.getTotalIncome()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotalExpenses()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getNetProfit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getProfitMargin()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getOrderStats().getTotalOrders()).isZero();
        assertThat(result.getOrderStats().getCompletedOrders()).isZero();
        assertThat(result.getOrderStats().getCancelledOrders()).isZero();
        assertThat(result.getOrderStats().getTotalItemsSold()).isZero();
        assertThat(result.getSoldItems()).isEmpty();
    }

    // ---------------------------------------------------------------------------
    // 6. getDashboard_usesShiftTimeRange
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("getDashboard uses ShiftTimeService to determine shift-aware date boundaries")
    void getDashboard_usesShiftTimeRange() {
        ZoneId zone = ZoneId.systemDefault();
        LocalTime lateOpen = LocalTime.of(21, 0);
        LocalTime lateClose = LocalTime.of(3, 0);

        // Overnight shift: 21:00 today to 21:00 tomorrow (extended to next opening)
        OffsetDateTime nightShiftStart = TODAY.atTime(lateOpen).atZone(zone).toOffsetDateTime();
        OffsetDateTime nightShiftEnd = TODAY.plusDays(1).atTime(lateOpen).atZone(zone).toOffsetDateTime();
        ShiftTimeRange nightShift = new ShiftTimeRange(nightShiftStart, nightShiftEnd, lateOpen, lateClose);

        when(shiftTimeService.getShiftTimeRangeForPeriod(RESTAURANT_ID, TODAY, TODAY))
                .thenReturn(nightShift);
        // Previous period shift for comparison calculation
        when(shiftTimeService.getShiftTimeRangeForPeriod(
                eq(RESTAURANT_ID), any(LocalDate.class), eq(TODAY.minusDays(1))))
                .thenReturn(previousPeriodShiftRange);
        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(
                eq(RESTAURANT_ID), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(expenseRepository.findByRestaurant_IdAndExpenseDateBetween(
                eq(RESTAURANT_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(payrollRepository.findByRestaurant_IdAndPayPeriodEndBetween(
                eq(RESTAURANT_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(ingredientRepository.findByRestaurant_IdAndActiveTrue(RESTAURANT_ID))
                .thenReturn(Collections.emptyList());

        DashboardResponse result = dashboardService.getDashboard(RESTAURANT_ID, TODAY, TODAY);

        // Verify ShiftTimeService was consulted
        verify(shiftTimeService).getShiftTimeRangeForPeriod(RESTAURANT_ID, TODAY, TODAY);

        // Verify orders were fetched using the shift-based time range
        verify(orderRepository).findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(
                RESTAURANT_ID, nightShiftStart, nightShiftEnd);

        // Verify shift info is populated in the response
        assertThat(result.getShiftTimeInfo()).isNotNull();
        assertThat(result.getShiftTimeInfo().getShiftStart()).isEqualTo(nightShiftStart);
        assertThat(result.getShiftTimeInfo().getShiftEnd()).isEqualTo(nightShiftEnd);
        assertThat(result.getShiftTimeInfo().getBusinessOpenTime()).isEqualTo(lateOpen);
        assertThat(result.getShiftTimeInfo().getBusinessCloseTime()).isEqualTo(lateClose);
    }

    // ---------------------------------------------------------------------------
    // 7. getDashboard_countsOrders
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("getDashboard counts total orders, completed orders, and cancelled orders")
    void getDashboard_countsOrders() {
        Order completed1 = createCompletedOrder(1L, BigDecimal.valueOf(100), List.of());
        Order completed2 = createCompletedOrder(2L, BigDecimal.valueOf(200), List.of());
        Order cancelled1 = createCancelledOrder(3L);
        Order cancelled2 = createCancelledOrder(4L);
        Order cancelled3 = createCancelledOrder(5L);

        stubDefaultMocks(
                List.of(completed1, completed2, cancelled1, cancelled2, cancelled3),
                Collections.emptyList(),
                Collections.emptyList()
        );

        DashboardResponse result = dashboardService.getDashboard(RESTAURANT_ID, TODAY, TODAY);

        assertThat(result.getOrderStats().getTotalOrders()).isEqualTo(5L);
        assertThat(result.getOrderStats().getCompletedOrders()).isEqualTo(2L);
        assertThat(result.getOrderStats().getCancelledOrders()).isEqualTo(3L);

        // Average order value = (100 + 200) / 2 = 150.00
        assertThat(result.getOrderStats().getAverageOrderValue())
                .isEqualByComparingTo(new BigDecimal("150.00"));
    }

    // ---------------------------------------------------------------------------
    // 8. getDashboard_topSellingItems
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("getDashboard aggregates sold items across orders and sorts by quantity descending")
    void getDashboard_topSellingItems() {
        OrderItem burger1 = createItem(10L, "Burger", 3, BigDecimal.valueOf(15));
        OrderItem burger2 = createItem(10L, "Burger", 2, BigDecimal.valueOf(15));
        OrderItem pizza = createItem(20L, "Pizza", 4, BigDecimal.valueOf(20));
        OrderItem salad = createItem(30L, "Salad", 1, BigDecimal.valueOf(10));

        Order order1 = createCompletedOrder(1L, BigDecimal.valueOf(125), List.of(burger1, pizza));
        Order order2 = createCompletedOrder(2L, BigDecimal.valueOf(40), List.of(burger2, salad));

        stubDefaultMocks(List.of(order1, order2), Collections.emptyList(), Collections.emptyList());

        // Product lookups for cost enrichment (return empty so no enrichment happens)
        when(productRepository.findById(10L)).thenReturn(Optional.empty());
        when(productRepository.findById(20L)).thenReturn(Optional.empty());
        when(productRepository.findById(30L)).thenReturn(Optional.empty());

        DashboardResponse result = dashboardService.getDashboard(RESTAURANT_ID, TODAY, TODAY);

        List<DashboardResponse.SoldItem> soldItems = result.getSoldItems();
        assertThat(soldItems).hasSize(3);

        // Sorted by quantity descending: Burger (5), Pizza (4), Salad (1)
        DashboardResponse.SoldItem topItem = soldItems.get(0);
        assertThat(topItem.getProductName()).isEqualTo("Burger");
        assertThat(topItem.getQuantitySold()).isEqualTo(5L);
        // Revenue: 3*15 + 2*15 = 45 + 30 = 75
        assertThat(topItem.getTotalRevenue()).isEqualByComparingTo(BigDecimal.valueOf(75));

        DashboardResponse.SoldItem secondItem = soldItems.get(1);
        assertThat(secondItem.getProductName()).isEqualTo("Pizza");
        assertThat(secondItem.getQuantitySold()).isEqualTo(4L);
        assertThat(secondItem.getTotalRevenue()).isEqualByComparingTo(BigDecimal.valueOf(80));

        DashboardResponse.SoldItem thirdItem = soldItems.get(2);
        assertThat(thirdItem.getProductName()).isEqualTo("Salad");
        assertThat(thirdItem.getQuantitySold()).isEqualTo(1L);
        assertThat(thirdItem.getTotalRevenue()).isEqualByComparingTo(BigDecimal.valueOf(10));
    }

    // ---------------------------------------------------------------------------
    // 9. getTodaySummary_delegatesToGetDashboard
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("getTodaySummary uses ShiftTimeService.getCurrentBusinessDay and delegates to getDashboard")
    void getTodaySummary_delegatesToGetDashboard() {
        LocalDate businessDay = LocalDate.of(2026, 3, 28);
        when(shiftTimeService.getCurrentBusinessDay(RESTAURANT_ID)).thenReturn(businessDay);

        // Mocks for getDashboard(restaurantId, businessDay, businessDay)
        when(shiftTimeService.getShiftTimeRangeForPeriod(
                eq(RESTAURANT_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(defaultShiftRange);
        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(
                eq(RESTAURANT_ID), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(expenseRepository.findByRestaurant_IdAndExpenseDateBetween(
                eq(RESTAURANT_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(payrollRepository.findByRestaurant_IdAndPayPeriodEndBetween(
                eq(RESTAURANT_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(ingredientRepository.findByRestaurant_IdAndActiveTrue(RESTAURANT_ID))
                .thenReturn(Collections.emptyList());

        DashboardResponse result = dashboardService.getTodaySummary(RESTAURANT_ID);

        verify(shiftTimeService).getCurrentBusinessDay(RESTAURANT_ID);
        verify(shiftTimeService).getShiftTimeRangeForPeriod(RESTAURANT_ID, businessDay, businessDay);
        assertThat(result).isNotNull();
        assertThat(result.getStartDate()).isEqualTo(businessDay);
        assertThat(result.getEndDate()).isEqualTo(businessDay);
    }

    // ---------------------------------------------------------------------------
    // 10. getWeekSummary_delegatesToGetDashboard
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("getWeekSummary calculates week start (Monday) and delegates to getDashboard")
    void getWeekSummary_delegatesToGetDashboard() {
        when(shiftTimeService.getShiftTimeRangeForPeriod(
                eq(RESTAURANT_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(defaultShiftRange);
        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(
                eq(RESTAURANT_ID), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(expenseRepository.findByRestaurant_IdAndExpenseDateBetween(
                eq(RESTAURANT_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(payrollRepository.findByRestaurant_IdAndPayPeriodEndBetween(
                eq(RESTAURANT_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(ingredientRepository.findByRestaurant_IdAndActiveTrue(RESTAURANT_ID))
                .thenReturn(Collections.emptyList());

        DashboardResponse result = dashboardService.getWeekSummary(RESTAURANT_ID);

        assertThat(result).isNotNull();
        // The start date must be a Monday (DayOfWeek value 1)
        assertThat(result.getStartDate().getDayOfWeek().getValue()).isEqualTo(1);
        // End date is today
        assertThat(result.getEndDate()).isEqualTo(LocalDate.now());
    }

    // ---------------------------------------------------------------------------
    // 11. getMonthSummary_delegatesToGetDashboard
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("getMonthSummary calculates month start (1st) and delegates to getDashboard")
    void getMonthSummary_delegatesToGetDashboard() {
        when(shiftTimeService.getShiftTimeRangeForPeriod(
                eq(RESTAURANT_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(defaultShiftRange);
        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(
                eq(RESTAURANT_ID), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(Collections.emptyList());
        when(expenseRepository.findByRestaurant_IdAndExpenseDateBetween(
                eq(RESTAURANT_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(payrollRepository.findByRestaurant_IdAndPayPeriodEndBetween(
                eq(RESTAURANT_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());
        when(ingredientRepository.findByRestaurant_IdAndActiveTrue(RESTAURANT_ID))
                .thenReturn(Collections.emptyList());

        DashboardResponse result = dashboardService.getMonthSummary(RESTAURANT_ID);

        assertThat(result).isNotNull();
        // Start date is the 1st of the current month
        assertThat(result.getStartDate().getDayOfMonth()).isEqualTo(1);
        assertThat(result.getStartDate().getMonth()).isEqualTo(LocalDate.now().getMonth());
        // End date is today
        assertThat(result.getEndDate()).isEqualTo(LocalDate.now());
    }

    // ---------------------------------------------------------------------------
    // 12. getDashboard_includesPreviousPeriodComparison
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("getDashboard includes previous period comparison with income/expense/profit changes and trend")
    void getDashboard_includesPreviousPeriodComparison() {
        // Current period: revenue 1000, expenses 300
        Order currentOrder = createCompletedOrder(1L, BigDecimal.valueOf(1000), List.of());
        Expense currentExpense = createPaidExpense(1L, BigDecimal.valueOf(300));

        // Previous period: revenue 500, expenses 200
        Order prevOrder = createCompletedOrder(2L, BigDecimal.valueOf(500), List.of());
        Expense prevExpense = createPaidExpense(2L, BigDecimal.valueOf(200));

        // Current period shift range
        when(shiftTimeService.getShiftTimeRangeForPeriod(RESTAURANT_ID, TODAY, TODAY))
                .thenReturn(defaultShiftRange);
        // Previous period shift range (called inside calculatePeriodComparison)
        LocalDate prevDate = TODAY.minusDays(1);
        when(shiftTimeService.getShiftTimeRangeForPeriod(RESTAURANT_ID, prevDate, prevDate))
                .thenReturn(previousPeriodShiftRange);

        // Current period orders
        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(
                RESTAURANT_ID, defaultShiftRange.start(), defaultShiftRange.end()))
                .thenReturn(List.of(currentOrder));
        // Previous period orders
        when(orderRepository.findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(
                RESTAURANT_ID, previousPeriodShiftRange.start(), previousPeriodShiftRange.end()))
                .thenReturn(List.of(prevOrder));

        // Current period expenses
        when(expenseRepository.findByRestaurant_IdAndExpenseDateBetween(RESTAURANT_ID, TODAY, TODAY))
                .thenReturn(List.of(currentExpense));
        // Previous period expenses
        when(expenseRepository.findByRestaurant_IdAndExpenseDateBetween(RESTAURANT_ID, prevDate, prevDate))
                .thenReturn(List.of(prevExpense));

        when(payrollRepository.findByRestaurant_IdAndPayPeriodEndBetween(
                eq(RESTAURANT_ID), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(Collections.emptyList());

        when(ingredientRepository.findByRestaurant_IdAndActiveTrue(RESTAURANT_ID))
                .thenReturn(Collections.emptyList());

        when(shiftTimeService.getBusinessDay(eq(RESTAURANT_ID), any()))
                .thenReturn(TODAY);

        DashboardResponse result = dashboardService.getDashboard(RESTAURANT_ID, TODAY, TODAY);

        DashboardResponse.PeriodComparison comparison = result.getComparison();
        assertThat(comparison).isNotNull();

        // Income change: (1000 - 500) / |500| * 100 = 100.0000%
        assertThat(comparison.getIncomeChange())
                .isEqualByComparingTo(new BigDecimal("100.0000"));

        // Expense change: (300 - 200) / |200| * 100 = 50.0000%
        assertThat(comparison.getExpenseChange())
                .isEqualByComparingTo(new BigDecimal("50.0000"));

        // Current profit = 1000 - 300 = 700; Previous profit = 500 - 200 = 300
        // Profit change: (700 - 300) / |300| * 100
        BigDecimal expectedProfitChange = BigDecimal.valueOf(400)
                .divide(BigDecimal.valueOf(300), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        assertThat(comparison.getProfitChange()).isEqualByComparingTo(expectedProfitChange);

        // Trend should be UP since profit change is well above 5%
        assertThat(comparison.getTrend()).isEqualTo("UP");
    }
}
