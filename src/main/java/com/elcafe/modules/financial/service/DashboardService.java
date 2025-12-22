package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.dto.DashboardResponse;
import com.elcafe.modules.financial.entity.Expense;
import com.elcafe.modules.financial.entity.PayrollEntry;
import com.elcafe.modules.financial.repository.ExpenseRepository;
import com.elcafe.modules.financial.repository.PayrollEntryRepository;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final OrderRepository orderRepository;
    private final ExpenseRepository expenseRepository;
    private final PayrollEntryRepository payrollRepository;
    private final InventoryIngredientRepository ingredientRepository;
    private final ProductRepository productRepository;

    // Completed order statuses that count as income
    private static final List<OrderStatus> COMPLETED_STATUSES = List.of(
            OrderStatus.DELIVERED,
            OrderStatus.READY,
            OrderStatus.PICKED_UP
    );

    /**
     * Get comprehensive dashboard data for a restaurant
     */
    public DashboardResponse getDashboard(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        log.info("Generating dashboard for restaurant {} from {} to {}", restaurantId, startDate, endDate);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

        // Fetch all orders in date range
        List<Order> allOrders = orderRepository.findByRestaurantIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                restaurantId, startDateTime, endDateTime);

        // Filter completed orders for income calculation
        List<Order> completedOrders = allOrders.stream()
                .filter(o -> COMPLETED_STATUSES.contains(o.getStatus()))
                .collect(Collectors.toList());

        // Calculate income
        BigDecimal totalIncome = completedOrders.stream()
                .map(Order::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Fetch expenses
        List<Expense> expenses = expenseRepository.findByRestaurant_IdAndExpenseDateBetween(
                restaurantId, startDate, endDate);

        BigDecimal totalExpenses = expenses.stream()
                .filter(e -> e.getPaymentStatus() == Expense.PaymentStatus.PAID)
                .map(Expense::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Fetch payroll
        List<PayrollEntry> payrollEntries = payrollRepository.findByRestaurant_IdAndPayPeriodEndBetween(
                restaurantId, startDate, endDate);

        BigDecimal totalPayroll = payrollEntries.stream()
                .filter(p -> p.getStatus() == PayrollEntry.PaymentStatus.PAID)
                .map(PayrollEntry::getNetPay)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Add payroll to total expenses
        totalExpenses = totalExpenses.add(totalPayroll);

        // Calculate net profit
        BigDecimal netProfit = totalIncome.subtract(totalExpenses);

        // Calculate profit margin
        BigDecimal profitMargin = BigDecimal.ZERO;
        if (totalIncome.compareTo(BigDecimal.ZERO) > 0) {
            profitMargin = netProfit.divide(totalIncome, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"));
        }

        // Build response
        return DashboardResponse.builder()
                .startDate(startDate)
                .endDate(endDate)
                .totalIncome(totalIncome)
                .totalExpenses(totalExpenses)
                .netProfit(netProfit)
                .profitMargin(profitMargin)
                .orderStats(buildOrderStats(allOrders, completedOrders))
                .incomeByOrderType(calculateIncomeByOrderType(completedOrders))
                .incomeByPaymentMethod(calculateIncomeByPaymentMethod(completedOrders))
                .expensesByCategory(calculateExpensesByCategory(expenses, totalPayroll))
                .dailyStats(calculateDailyStats(completedOrders, expenses, startDate, endDate))
                .topSellingItems(calculateTopSellingItems(completedOrders))
                .comparison(calculatePeriodComparison(restaurantId, startDate, endDate, totalIncome, totalExpenses, allOrders.size()))
                .inventoryAlerts(calculateInventoryAlerts(restaurantId))
                .build();
    }

    /**
     * Get quick summary for the current day
     */
    public DashboardResponse getTodaySummary(Long restaurantId) {
        LocalDate today = LocalDate.now();
        return getDashboard(restaurantId, today, today);
    }

    /**
     * Get summary for current week
     */
    public DashboardResponse getWeekSummary(Long restaurantId) {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1);
        return getDashboard(restaurantId, weekStart, today);
    }

    /**
     * Get summary for current month
     */
    public DashboardResponse getMonthSummary(Long restaurantId) {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        return getDashboard(restaurantId, monthStart, today);
    }

    private DashboardResponse.OrderStats buildOrderStats(List<Order> allOrders, List<Order> completedOrders) {
        long totalOrders = allOrders.size();
        long completed = completedOrders.size();
        long cancelled = allOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.CANCELLED)
                .count();

        BigDecimal totalRevenue = completedOrders.stream()
                .map(Order::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgOrderValue = BigDecimal.ZERO;
        if (completed > 0) {
            avgOrderValue = totalRevenue.divide(BigDecimal.valueOf(completed), 2, RoundingMode.HALF_UP);
        }

        long totalItemsSold = completedOrders.stream()
                .flatMap(o -> o.getItems().stream())
                .mapToLong(OrderItem::getQuantity)
                .sum();

        return DashboardResponse.OrderStats.builder()
                .totalOrders(totalOrders)
                .completedOrders(completed)
                .cancelledOrders(cancelled)
                .averageOrderValue(avgOrderValue)
                .totalItemsSold(totalItemsSold)
                .build();
    }

    private Map<String, BigDecimal> calculateIncomeByOrderType(List<Order> orders) {
        return orders.stream()
                .filter(o -> o.getTotal() != null)
                .collect(Collectors.groupingBy(
                        o -> o.getOrderType() != null ? o.getOrderType().name() : "UNKNOWN",
                        Collectors.reducing(BigDecimal.ZERO, Order::getTotal, BigDecimal::add)
                ));
    }

    private Map<String, BigDecimal> calculateIncomeByPaymentMethod(List<Order> orders) {
        Map<String, BigDecimal> result = new HashMap<>();

        for (Order order : orders) {
            if (order.getTotal() != null && order.getPayments() != null && !order.getPayments().isEmpty()) {
                // Use the first payment method (for split payments, this is simplified)
                String method = order.getPayments().get(0).getMethod() != null
                        ? order.getPayments().get(0).getMethod().name()
                        : "UNKNOWN";
                result.merge(method, order.getTotal(), BigDecimal::add);
            } else if (order.getTotal() != null) {
                result.merge("CASH", order.getTotal(), BigDecimal::add);
            }
        }

        return result;
    }

    private Map<String, BigDecimal> calculateExpensesByCategory(List<Expense> expenses, BigDecimal totalPayroll) {
        Map<String, BigDecimal> result = expenses.stream()
                .filter(e -> e.getPaymentStatus() == Expense.PaymentStatus.PAID)
                .filter(e -> e.getTotalAmount() != null)
                .collect(Collectors.groupingBy(
                        e -> e.getCategory() != null ? e.getCategory().name() : "OTHER",
                        Collectors.reducing(BigDecimal.ZERO, Expense::getTotalAmount, BigDecimal::add)
                ));

        // Add payroll as a category
        if (totalPayroll.compareTo(BigDecimal.ZERO) > 0) {
            result.put("PAYROLL", totalPayroll);
        }

        return result;
    }

    private List<DashboardResponse.DailyStats> calculateDailyStats(
            List<Order> orders, List<Expense> expenses, LocalDate startDate, LocalDate endDate) {

        List<DashboardResponse.DailyStats> dailyStats = new ArrayList<>();

        // Group orders by date
        Map<LocalDate, List<Order>> ordersByDate = orders.stream()
                .collect(Collectors.groupingBy(o -> o.getCreatedAt().toLocalDate()));

        // Group expenses by date
        Map<LocalDate, List<Expense>> expensesByDate = expenses.stream()
                .filter(e -> e.getPaymentStatus() == Expense.PaymentStatus.PAID)
                .collect(Collectors.groupingBy(Expense::getExpenseDate));

        // Iterate through each day
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            List<Order> dayOrders = ordersByDate.getOrDefault(date, Collections.emptyList());
            List<Expense> dayExpenses = expensesByDate.getOrDefault(date, Collections.emptyList());

            BigDecimal income = dayOrders.stream()
                    .map(Order::getTotal)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal expenseTotal = dayExpenses.stream()
                    .map(Expense::getTotalAmount)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            dailyStats.add(DashboardResponse.DailyStats.builder()
                    .date(date)
                    .income(income)
                    .expenses(expenseTotal)
                    .orderCount((long) dayOrders.size())
                    .netProfit(income.subtract(expenseTotal))
                    .build());
        }

        return dailyStats;
    }

    private List<DashboardResponse.TopItem> calculateTopSellingItems(List<Order> orders) {
        // Aggregate items across all orders
        Map<Long, DashboardResponse.TopItem> itemsMap = new HashMap<>();

        for (Order order : orders) {
            for (OrderItem item : order.getItems()) {
                Long productId = item.getProductId();
                DashboardResponse.TopItem existing = itemsMap.get(productId);

                if (existing == null) {
                    itemsMap.put(productId, DashboardResponse.TopItem.builder()
                            .productId(productId)
                            .productName(item.getProductName())
                            .quantitySold((long) item.getQuantity())
                            .totalRevenue(item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO)
                            .build());
                } else {
                    existing.setQuantitySold(existing.getQuantitySold() + item.getQuantity());
                    existing.setTotalRevenue(existing.getTotalRevenue()
                            .add(item.getTotalPrice() != null ? item.getTotalPrice() : BigDecimal.ZERO));
                }
            }
        }

        // Sort by quantity sold and take top 10
        List<DashboardResponse.TopItem> topItems = itemsMap.values().stream()
                .sorted((a, b) -> Long.compare(b.getQuantitySold(), a.getQuantitySold()))
                .limit(10)
                .collect(Collectors.toList());

        // Enrich with cost and profit data
        for (DashboardResponse.TopItem item : topItems) {
            try {
                Product product = productRepository.findById(item.getProductId()).orElse(null);
                if (product != null && product.getCostPrice() != null) {
                    BigDecimal costPrice = product.getCostPrice();
                    BigDecimal totalCost = costPrice.multiply(BigDecimal.valueOf(item.getQuantitySold()));
                    BigDecimal profit = item.getTotalRevenue().subtract(totalCost);
                    BigDecimal profitMargin = BigDecimal.ZERO;

                    if (item.getTotalRevenue().compareTo(BigDecimal.ZERO) > 0) {
                        profitMargin = profit.divide(item.getTotalRevenue(), 4, RoundingMode.HALF_UP)
                                .multiply(new BigDecimal("100"));
                    }

                    item.setCostPrice(costPrice);
                    item.setTotalCost(totalCost);
                    item.setProfit(profit);
                    item.setProfitMargin(profitMargin);
                }
            } catch (Exception e) {
                log.warn("Failed to get cost data for product {}: {}", item.getProductId(), e.getMessage());
            }
        }

        return topItems;
    }

    private DashboardResponse.PeriodComparison calculatePeriodComparison(
            Long restaurantId, LocalDate startDate, LocalDate endDate,
            BigDecimal currentIncome, BigDecimal currentExpenses, int currentOrderCount) {

        // Calculate previous period with same duration
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        LocalDate prevEndDate = startDate.minusDays(1);
        LocalDate prevStartDate = prevEndDate.minusDays(daysBetween - 1);

        LocalDateTime prevStartDateTime = prevStartDate.atStartOfDay();
        LocalDateTime prevEndDateTime = prevEndDate.atTime(LocalTime.MAX);

        // Get previous period orders
        List<Order> prevOrders = orderRepository.findByRestaurantIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                restaurantId, prevStartDateTime, prevEndDateTime);

        List<Order> prevCompletedOrders = prevOrders.stream()
                .filter(o -> COMPLETED_STATUSES.contains(o.getStatus()))
                .collect(Collectors.toList());

        BigDecimal prevIncome = prevCompletedOrders.stream()
                .map(Order::getTotal)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Get previous period expenses
        List<Expense> prevExpenses = expenseRepository.findByRestaurant_IdAndExpenseDateBetween(
                restaurantId, prevStartDate, prevEndDate);

        BigDecimal prevExpenseTotal = prevExpenses.stream()
                .filter(e -> e.getPaymentStatus() == Expense.PaymentStatus.PAID)
                .map(Expense::getTotalAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate changes
        BigDecimal incomeChange = calculatePercentageChange(prevIncome, currentIncome);
        BigDecimal expenseChange = calculatePercentageChange(prevExpenseTotal, currentExpenses);

        BigDecimal prevProfit = prevIncome.subtract(prevExpenseTotal);
        BigDecimal currentProfit = currentIncome.subtract(currentExpenses);
        BigDecimal profitChange = calculatePercentageChange(prevProfit, currentProfit);

        long orderCountChange = 0;
        if (prevOrders.size() > 0) {
            orderCountChange = ((currentOrderCount - prevOrders.size()) * 100L) / prevOrders.size();
        }

        // Determine trend
        String trend = "STABLE";
        if (profitChange.compareTo(new BigDecimal("5")) > 0) {
            trend = "UP";
        } else if (profitChange.compareTo(new BigDecimal("-5")) < 0) {
            trend = "DOWN";
        }

        return DashboardResponse.PeriodComparison.builder()
                .incomeChange(incomeChange)
                .expenseChange(expenseChange)
                .profitChange(profitChange)
                .orderCountChange(orderCountChange)
                .trend(trend)
                .build();
    }

    private BigDecimal calculatePercentageChange(BigDecimal previous, BigDecimal current) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) > 0 ? new BigDecimal("100") : BigDecimal.ZERO;
        }
        return current.subtract(previous)
                .divide(previous.abs(), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    private DashboardResponse.InventoryAlerts calculateInventoryAlerts(Long restaurantId) {
        // Fetch all active ingredients
        List<Ingredient> allIngredients = ingredientRepository.findByRestaurantIdAndActiveTrue(restaurantId);

        // Filter low stock items (currentStock <= minimumStock)
        List<Ingredient> lowStockIngredients = allIngredients.stream()
                .filter(i -> i.getCurrentStock().compareTo(i.getMinimumStock()) <= 0)
                .collect(Collectors.toList());

        // Filter reorder items (currentStock <= reorderLevel but > minimumStock)
        long reorderCount = allIngredients.stream()
                .filter(i -> i.getCurrentStock().compareTo(i.getReorderLevel()) <= 0
                        && i.getCurrentStock().compareTo(i.getMinimumStock()) > 0)
                .count();

        // Build low stock items list (top 5 most critical)
        List<DashboardResponse.LowStockItem> lowStockItems = lowStockIngredients.stream()
                .sorted((a, b) -> {
                    // Sort by criticality (lower stock ratio = more critical)
                    BigDecimal ratioA = a.getMinimumStock().compareTo(BigDecimal.ZERO) > 0
                            ? a.getCurrentStock().divide(a.getMinimumStock(), 4, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    BigDecimal ratioB = b.getMinimumStock().compareTo(BigDecimal.ZERO) > 0
                            ? b.getCurrentStock().divide(b.getMinimumStock(), 4, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    return ratioA.compareTo(ratioB);
                })
                .limit(5)
                .map(i -> DashboardResponse.LowStockItem.builder()
                        .ingredientId(i.getId())
                        .ingredientName(i.getName())
                        .currentStock(i.getCurrentStock())
                        .minimumStock(i.getMinimumStock())
                        .reorderLevel(i.getReorderLevel())
                        .unit(i.getUnit())
                        .supplierName(i.getSupplierEntity() != null ? i.getSupplierEntity().getName() : i.getSupplier())
                        .alertLevel(i.getCurrentStock().compareTo(BigDecimal.ZERO) <= 0 ? "CRITICAL" : "LOW")
                        .build())
                .collect(Collectors.toList());

        return DashboardResponse.InventoryAlerts.builder()
                .lowStockCount((long) lowStockIngredients.size())
                .reorderCount(reorderCount)
                .expiringCount(0L) // TODO: Implement expiry tracking
                .lowStockItems(lowStockItems)
                .build();
    }
}
