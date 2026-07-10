package com.elcafe.modules.order.repository;

import com.elcafe.modules.order.dto.CouponSalesRow;
import com.elcafe.modules.order.dto.CustomerActivityRow;
import com.elcafe.modules.order.dto.CustomerLifetimeRow;
import com.elcafe.modules.order.dto.CustomerOrderCountRow;
import com.elcafe.modules.order.dto.CustomerOrderStatsRow;
import com.elcafe.modules.order.dto.CustomerSourceRow;
import com.elcafe.modules.order.dto.DiscountOrderRow;
import com.elcafe.modules.order.dto.HourlySalesRow;
import com.elcafe.modules.order.dto.OrderTimingRow;
import com.elcafe.modules.order.dto.OrderVolumeCountsRow;
import com.elcafe.modules.order.dto.PnlOrderRow;
import com.elcafe.modules.order.dto.ProductSalesRow;
import com.elcafe.modules.order.dto.RevenueOrderRow;
import com.elcafe.modules.order.dto.RevenueTotalsRow;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.waiter.entity.Waiter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    /** Range + optional-tenant base shared by every analytics aggregate on this repository. */
    String RANGE_TENANT_WHERE = """
             o.createdAt BETWEEN :start AND :end
             AND (:restaurantId IS NULL OR o.restaurant.id = :restaurantId)
            """;

    /**
     * Exact SQL translation of {@code Order.isFullyPaid()}: effective total (grandTotal if &gt; 0, else
     * total) must be positive and covered by the sum of COMPLETED payments' net amounts
     * (amount + tip − refunded, {@code Payment.getNetAmount()}).
     */
    String FULLY_PAID_PREDICATE = """
             ( (CASE WHEN o.grandTotal IS NOT NULL AND o.grandTotal > 0 THEN o.grandTotal ELSE COALESCE(o.total, 0) END) > 0
               AND COALESCE((SELECT SUM(p.amount + COALESCE(p.tipAmount, 0) - COALESCE(p.refundedAmount, 0))
                             FROM Payment p WHERE p.order = o AND p.status = :completedPayment), 0)
                   >= (CASE WHEN o.grandTotal IS NOT NULL AND o.grandTotal > 0 THEN o.grandTotal ELSE COALESCE(o.total, 0) END) )
            """;

    /**
     * "Revenue-qualifying" orders (FinancialAnalyticsService / FinancialReportsService filter): not
     * CANCELLED, and (revenue status OR paymentStatus COMPLETED OR fully paid). Deliberately does NOT
     * filter {@code deletedAt} — the Java filter it replaced never did, and changing which orders count
     * is not the aggregate rewrite's job.
     */
    String REVENUE_QUALIFYING_WHERE = RANGE_TENANT_WHERE
            + " AND o.status <> :cancelled "
            + " AND ( o.status IN :revenueStatuses OR o.paymentStatus = :completedPayment OR " + FULLY_PAID_PREDICATE + " ) ";

    /**
     * Status-only revenue filter (Operational/Inventory/Customer analytics): just
     * {@code status IN REVENUE_STATUSES}. CANCELLED can never match (it is not a revenue status), so the
     * old code's separate not-CANCELLED check is subsumed.
     */
    String REVENUE_STATUS_WHERE = RANGE_TENANT_WHERE + " AND o.status IN :revenueStatuses ";

    /**
     * Paid-only filter (promotion analytics): not CANCELLED and (paymentStatus COMPLETED OR fully paid)
     * — the qualifying filter without the revenue-status branch.
     */
    String PAID_ORDER_WHERE = RANGE_TENANT_WHERE
            + " AND o.status <> :cancelled "
            + " AND ( o.paymentStatus = :completedPayment OR " + FULLY_PAID_PREDICATE + " ) ";

    /**
     * One scalar row per revenue-qualifying order (no entity graphs). The first-payment subquery is the
     * deterministic form of {@code Order.getPayment()} (first element ≙ lowest id).
     */
    @Query("SELECT new com.elcafe.modules.order.dto.RevenueOrderRow(o.createdAt, o.total, "
            + " (SELECT p1.method FROM Payment p1 WHERE p1.order = o AND p1.id = "
            + "   (SELECT MIN(p2.id) FROM Payment p2 WHERE p2.order = o))) "
            + "FROM Order o WHERE " + REVENUE_QUALIFYING_WHERE)
    List<RevenueOrderRow> findRevenueOrderRows(
            @Param("restaurantId") Long restaurantId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("cancelled") OrderStatus cancelled,
            @Param("revenueStatuses") Collection<OrderStatus> revenueStatuses,
            @Param("completedPayment") PaymentStatus completedPayment);

    /** Per-product line-total and quantity sums over revenue-qualifying orders, aggregated in the DB. */
    @Query("SELECT new com.elcafe.modules.order.dto.ProductSalesRow(oi.productId, SUM(oi.totalPrice), SUM(oi.quantity)) "
            + "FROM OrderItem oi JOIN oi.order o WHERE oi.productId IS NOT NULL AND " + REVENUE_QUALIFYING_WHERE
            + " GROUP BY oi.productId")
    List<ProductSalesRow> sumProductSales(
            @Param("restaurantId") Long restaurantId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("cancelled") OrderStatus cancelled,
            @Param("revenueStatuses") Collection<OrderStatus> revenueStatuses,
            @Param("completedPayment") PaymentStatus completedPayment);

    /** Total revenue (SUM of order totals) and order count over revenue-qualifying orders. */
    @Query("SELECT new com.elcafe.modules.order.dto.RevenueTotalsRow(COALESCE(SUM(o.total), 0), COUNT(o)) "
            + "FROM Order o WHERE " + REVENUE_QUALIFYING_WHERE)
    RevenueTotalsRow sumRevenueTotals(
            @Param("restaurantId") Long restaurantId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("cancelled") OrderStatus cancelled,
            @Param("revenueStatuses") Collection<OrderStatus> revenueStatuses,
            @Param("completedPayment") PaymentStatus completedPayment);

    /** Per-hour revenue/count over revenue-status orders (sales-per-hour analytics). */
    @Query("SELECT new com.elcafe.modules.order.dto.HourlySalesRow(EXTRACT(HOUR FROM o.createdAt), "
            + " COALESCE(SUM(o.total), 0), COUNT(o)) FROM Order o WHERE " + REVENUE_STATUS_WHERE
            + " GROUP BY EXTRACT(HOUR FROM o.createdAt)")
    List<HourlySalesRow> findHourlySales(
            @Param("restaurantId") Long restaurantId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("revenueStatuses") Collection<OrderStatus> revenueStatuses);

    /** Count of revenue-status orders with no delivery info — i.e. dine-in (table turnover). */
    @Query("SELECT COUNT(o) FROM Order o WHERE " + REVENUE_STATUS_WHERE
            + " AND NOT EXISTS (SELECT d.id FROM DeliveryInfo d WHERE d.order = o)")
    long countDineInRevenueOrders(
            @Param("restaurantId") Long restaurantId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("revenueStatuses") Collection<OrderStatus> revenueStatuses);

    /**
     * One scalar timing row per revenue-status order: kitchen prep minutes, delivery-info presence and
     * actual delivery time, history row count, and the first NEW/READY/DELIVERED history timestamps —
     * all via correlated subselects, replacing the per-order KitchenOrder + status-history N+1s.
     */
    @Query("SELECT new com.elcafe.modules.order.dto.OrderTimingRow(o.id, o.status, o.createdAt, o.updatedAt, "
            + " d.id, d.actualDeliveryTime, "
            + " (SELECT MIN(k.actualPreparationTimeMinutes) FROM KitchenOrder k WHERE k.order = o), "
            + " (SELECT COUNT(h) FROM OrderStatusHistory h WHERE h.order = o), "
            + " (SELECT MIN(h1.createdAt) FROM OrderStatusHistory h1 WHERE h1.order = o AND h1.status = :newStatus), "
            + " (SELECT MIN(h2.createdAt) FROM OrderStatusHistory h2 WHERE h2.order = o AND h2.status = :readyStatus), "
            + " (SELECT MIN(h3.createdAt) FROM OrderStatusHistory h3 WHERE h3.order = o AND h3.status = :deliveredStatus)) "
            + "FROM Order o LEFT JOIN DeliveryInfo d ON d.order = o WHERE " + REVENUE_STATUS_WHERE)
    List<OrderTimingRow> findOrderTimingRows(
            @Param("restaurantId") Long restaurantId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("revenueStatuses") Collection<OrderStatus> revenueStatuses,
            @Param("newStatus") OrderStatus newStatus,
            @Param("readyStatus") OrderStatus readyStatus,
            @Param("deliveredStatus") OrderStatus deliveredStatus);

    /** Per-product sums over revenue-STATUS orders (Inventory analytics' simpler filter). */
    @Query("SELECT new com.elcafe.modules.order.dto.ProductSalesRow(oi.productId, SUM(oi.totalPrice), SUM(oi.quantity)) "
            + "FROM OrderItem oi JOIN oi.order o WHERE oi.productId IS NOT NULL AND " + REVENUE_STATUS_WHERE
            + " GROUP BY oi.productId")
    List<ProductSalesRow> sumProductSalesByStatus(
            @Param("restaurantId") Long restaurantId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("revenueStatuses") Collection<OrderStatus> revenueStatuses);

    /** Per-customer order count over revenue-status orders in the range, with customer creation time. */
    @Query("SELECT new com.elcafe.modules.order.dto.CustomerOrderStatsRow(c.id, COUNT(o), c.createdAt) "
            + "FROM Order o JOIN o.customer c WHERE " + REVENUE_STATUS_WHERE
            + " GROUP BY c.id, c.createdAt")
    List<CustomerOrderStatsRow> findCustomerOrderStats(
            @Param("restaurantId") Long restaurantId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("revenueStatuses") Collection<OrderStatus> revenueStatuses);

    /**
     * Lifetime (no date range) per-active-customer aggregate over revenue-status orders. Replaces the
     * LTV path that loaded every active customer's entire order history one customer at a time.
     */
    @Query("SELECT new com.elcafe.modules.order.dto.CustomerLifetimeRow(c.id, COALESCE(SUM(o.total), 0), COUNT(o), "
            + " MIN(o.createdAt), MAX(o.createdAt)) "
            + "FROM Order o JOIN o.customer c "
            + "WHERE c.active = true AND (:restaurantId IS NULL OR o.restaurant.id = :restaurantId) "
            + " AND o.status IN :revenueStatuses "
            + "GROUP BY c.id")
    List<CustomerLifetimeRow> findCustomerLifetimeStats(
            @Param("restaurantId") Long restaurantId,
            @Param("revenueStatuses") Collection<OrderStatus> revenueStatuses);

    /** Total / revenue-status / cancelled counts over ALL orders in the range (satisfaction proxy). */
    @Query("SELECT new com.elcafe.modules.order.dto.OrderVolumeCountsRow(COUNT(o), "
            + " COALESCE(SUM(CASE WHEN o.status IN :revenueStatuses THEN 1L ELSE 0L END), 0L), "
            + " COALESCE(SUM(CASE WHEN o.status = :cancelled THEN 1L ELSE 0L END), 0L)) "
            + "FROM Order o WHERE " + RANGE_TENANT_WHERE)
    OrderVolumeCountsRow countOrderVolumes(
            @Param("restaurantId") Long restaurantId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("revenueStatuses") Collection<OrderStatus> revenueStatuses,
            @Param("cancelled") OrderStatus cancelled);

    /** Per-customer order counts over ALL orders in the range (repeat-customer rate). */
    @Query("SELECT new com.elcafe.modules.order.dto.CustomerOrderCountRow(o.customer.id, COUNT(o)) "
            + "FROM Order o WHERE " + RANGE_TENANT_WHERE + " AND o.customer IS NOT NULL "
            + "GROUP BY o.customer.id")
    List<CustomerOrderCountRow> findCustomerOrderCounts(
            @Param("restaurantId") Long restaurantId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end);

    /**
     * Lifetime per-customer activity (count / total spent / last order) over ALL orders, no status
     * filter — the RFM listing's semantics. One grouped query instead of three per customer.
     */
    @Query("SELECT new com.elcafe.modules.order.dto.CustomerActivityRow(o.customer.id, COUNT(o), "
            + " COALESCE(SUM(o.total), 0), MAX(o.createdAt)) "
            + "FROM Order o WHERE o.customer IS NOT NULL GROUP BY o.customer.id")
    List<CustomerActivityRow> findCustomerActivityRows();

    /** Distinct (customer, order source) pairs over all orders — batch form of the per-customer lookup. */
    @Query("SELECT DISTINCT new com.elcafe.modules.order.dto.CustomerSourceRow(o.customer.id, o.orderSource) "
            + "FROM Order o WHERE o.customer IS NOT NULL")
    List<CustomerSourceRow> findCustomerOrderSources();

    /** One scalar discount row per paid order (promotion analytics + daily discount trends). */
    @Query("SELECT new com.elcafe.modules.order.dto.DiscountOrderRow(o.createdAt, o.total, o.discount, o.discountType) "
            + "FROM Order o WHERE " + PAID_ORDER_WHERE)
    List<DiscountOrderRow> findPaidDiscountOrderRows(
            @Param("restaurantId") Long restaurantId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("cancelled") OrderStatus cancelled,
            @Param("completedPayment") PaymentStatus completedPayment);

    /** Per-coupon redemption/revenue/discount sums over non-cancelled orders carrying a coupon code. */
    @Query("SELECT new com.elcafe.modules.order.dto.CouponSalesRow(o.couponCode, COUNT(o), "
            + " COALESCE(SUM(o.total), 0), COALESCE(SUM(o.discount), 0)) "
            + "FROM Order o WHERE " + RANGE_TENANT_WHERE
            + " AND o.status <> :cancelled AND o.couponCode IS NOT NULL AND o.couponCode <> '' "
            + "GROUP BY o.couponCode")
    List<CouponSalesRow> sumCouponSales(
            @Param("restaurantId") Long restaurantId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("cancelled") OrderStatus cancelled);

    /** One scalar money row per revenue-qualifying order (P&L report). */
    @Query("SELECT new com.elcafe.modules.order.dto.PnlOrderRow(o.subtotal, o.serviceFee, o.deliveryFee, "
            + " o.tipAmount, o.discount, o.total, o.discountType) "
            + "FROM Order o WHERE " + REVENUE_QUALIFYING_WHERE)
    List<PnlOrderRow> findPnlOrderRows(
            @Param("restaurantId") Long restaurantId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("cancelled") OrderStatus cancelled,
            @Param("revenueStatuses") Collection<OrderStatus> revenueStatuses,
            @Param("completedPayment") PaymentStatus completedPayment);

    /**
     * Deliberately NO {@code @EntityGraph} here: {@code items} is a collection, and a collection
     * fetch combined with {@code Pageable} makes Hibernate paginate IN MEMORY (HHH90003004) — it
     * materialises every matching order of the tenant to return one page (surfaced by the E2 load
     * test: one warning per listing request). Pagination stays in SQL; the payload associations are
     * batch-initialised afterwards by {@code OrderJsonHydration} (a handful of {@code @BatchSize}
     * IN-selects per page).
     */
    @Override
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);

    /**
     * Loader for code that reads an order OUTSIDE any session — the {@code @Async} notification
     * paths (admin-panel WebSocket broadcast, owner-bot Telegram) hand orders across threads after
     * the loading transaction is gone, so everything they read must be initialised here: items,
     * waiter, dining table, customer, restaurant. Explicit {@code JOIN FETCH} because the
     * {@code @EntityGraph} that used to sit on a redeclared {@code findById} was silently IGNORED
     * (Spring Data resolves the override as a derived query and the graph hint never reached the
     * SQL — verified empirically; {@code OrderAsyncNotificationLoadTest} pins the working variant).
     * Single-row by id: the one collection fetch is safe — never add a {@code Pageable} here.
     */
    @Query("SELECT o FROM Order o "
            + "LEFT JOIN FETCH o.items "
            + "LEFT JOIN FETCH o.waiter "
            + "LEFT JOIN FETCH o.diningTable "
            + "LEFT JOIN FETCH o.customer "
            + "LEFT JOIN FETCH o.restaurant "
            + "WHERE o.id = :id")
    Optional<Order> findByIdForNotification(@Param("id") Long id);

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByRestaurant_IdAndStatusOrderByCreatedAtDesc(Long restaurantId, OrderStatus status);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.restaurant.id = :restaurantId AND o.status = :status ORDER BY o.createdAt DESC")
    List<Order> findByRestaurant_IdAndStatusWithItemsOrderByCreatedAtDesc(@Param("restaurantId") Long restaurantId, @Param("status") OrderStatus status);

    List<Order> findByRestaurant_IdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long restaurantId,
            OffsetDateTime startDate,
            OffsetDateTime endDate
    );

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.restaurant.id = :restaurantId AND o.createdAt BETWEEN :startDate AND :endDate ORDER BY o.createdAt DESC")
    List<Order> findByRestaurant_IdAndCreatedAtBetweenWithItemsOrderByCreatedAtDesc(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate
    );

    List<Order> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    /** Bounded variant — pass a {@code PageRequest} to cap how much history is materialized (PERF-9). */
    List<Order> findByCustomer_IdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.customer.id = :customerId ORDER BY o.createdAt DESC")
    List<Order> findByCustomer_IdWithItemsOrderByCreatedAtDesc(@Param("customerId") Long customerId);

    List<Order> findByStatusOrderByCreatedAtAsc(OrderStatus status);

    List<Order> findByWaiterAndStatusInOrderByCreatedAtDesc(Waiter waiter, List<OrderStatus> statuses);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.waiter = :waiter AND o.status IN :statuses ORDER BY o.createdAt DESC")
    List<Order> findByWaiterAndStatusInWithItemsOrderByCreatedAtDesc(@Param("waiter") Waiter waiter, @Param("statuses") List<OrderStatus> statuses);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.waiter = :waiter ORDER BY o.createdAt DESC")
    List<Order> findByWaiterWithItemsOrderByCreatedAtDesc(@Param("waiter") Waiter waiter);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.shiftId = :shiftId ORDER BY o.createdAt DESC")
    List<Order> findByShiftIdWithItems(@Param("shiftId") Long shiftId);

    List<Order> findByRestaurant_IdAndStatus(Long restaurantId, OrderStatus status);

    // Find active orders by table
    List<Order> findByDiningTable_IdAndStatusIn(Long tableId, List<OrderStatus> statuses);

    List<Order> findByStatus(OrderStatus status);

    /** Bounded variant — caps a cross-tenant status scan to one page (PERF-9). */
    List<Order> findByStatus(OrderStatus status, Pageable pageable);

    /** Count-only variants of {@code findByCreatedAtBetween} for metrics jobs (PERF-8). */
    long countByCreatedAtBetween(OffsetDateTime startDate, OffsetDateTime endDate);

    long countByStatusAndCreatedAtBetween(OrderStatus status, OffsetDateTime startDate, OffsetDateTime endDate);

    List<Order> findByDeliveryInfo_CourierId(Long courierId);

    List<Order> findByStatusAndPlacedAtBefore(OrderStatus status, OffsetDateTime placedAt);

    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, OffsetDateTime createdAt);

    List<Order> findByCreatedAtBetween(OffsetDateTime startDate, OffsetDateTime endDate);

    Optional<Order> findByPaymentIntentId(String paymentIntentId);

    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE o.customer.id = :customerId")
    BigDecimal sumTotalByCustomerId(@Param("customerId") Long customerId);

    long countByCustomer_Id(Long customerId);

    @Query("SELECT DISTINCT o.orderSource FROM Order o WHERE o.customer.id = :customerId")
    List<OrderSource> findDistinctOrderSourcesByCustomerId(@Param("customerId") Long customerId);

    // Waiter metrics queries (all-time - backward compatibility)
    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE o.waiter.id = :waiterId AND o.status NOT IN ('CANCELLED') AND (o.status NOT IN ('PENDING', 'NEW') OR o.paymentStatus = 'COMPLETED')")
    BigDecimal calculateTotalRevenueByWaiter(@Param("waiterId") Long waiterId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.waiter.id = :waiterId AND o.status NOT IN ('CANCELLED') AND (o.status NOT IN ('PENDING', 'NEW') OR o.paymentStatus = 'COMPLETED')")
    Long countValidOrdersByWaiter(@Param("waiterId") Long waiterId);

    // Waiter metrics queries (with date filter for period support)
    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE o.waiter.id = :waiterId AND o.status NOT IN ('CANCELLED') AND (o.status NOT IN ('PENDING', 'NEW') OR o.paymentStatus = 'COMPLETED') AND o.createdAt >= :startDate")
    BigDecimal calculateTotalRevenueByWaiterSince(@Param("waiterId") Long waiterId, @Param("startDate") OffsetDateTime startDate);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.waiter.id = :waiterId AND o.status NOT IN ('CANCELLED') AND (o.status NOT IN ('PENDING', 'NEW') OR o.paymentStatus = 'COMPLETED') AND o.createdAt >= :startDate")
    Long countValidOrdersByWaiterSince(@Param("waiterId") Long waiterId, @Param("startDate") OffsetDateTime startDate);

    @Query("SELECT o FROM Order o WHERE o.waiter.id = :waiterId AND o.status NOT IN ('CANCELLED') AND (o.status NOT IN ('PENDING', 'NEW') OR o.paymentStatus = 'COMPLETED') ORDER BY o.createdAt DESC")
    List<Order> findRecentOrdersByWaiter(@Param("waiterId") Long waiterId, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.waiter.id = :waiterId AND o.status NOT IN ('CANCELLED') AND (o.status NOT IN ('PENDING', 'NEW') OR o.paymentStatus = 'COMPLETED') AND o.createdAt >= :startDate ORDER BY o.createdAt DESC")
    List<Order> findRecentOrdersByWaiterSince(@Param("waiterId") Long waiterId, @Param("startDate") OffsetDateTime startDate, Pageable pageable);

    @Query("SELECT CAST(o.createdAt AS LocalDate) as date, COALESCE(SUM(o.total), 0) as revenue, COUNT(o) as orderCount " +
           "FROM Order o " +
           "WHERE o.waiter.id = :waiterId " +
           "AND o.status NOT IN ('CANCELLED') " +
           "AND (o.status NOT IN ('PENDING', 'NEW') OR o.paymentStatus = 'COMPLETED') " +
           "AND o.createdAt >= :startDate " +
           "GROUP BY CAST(o.createdAt AS LocalDate) " +
           "ORDER BY CAST(o.createdAt AS LocalDate) DESC")
    List<Object[]> findDailyRevenueByWaiter(@Param("waiterId") Long waiterId, @Param("startDate") OffsetDateTime startDate);

    // POS: Find open dine-in orders for a restaurant (includes orders with orderTables, diningTable, or legacy tableIds)
    // Note: only JOIN FETCH items (one collection) to avoid cartesian product duplicates with List<OrderItem>.
    // orderTables are loaded via @BatchSize on the entity.
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.restaurant.id = :restaurantId AND (SIZE(o.orderTables) > 0 OR o.diningTable IS NOT NULL OR o.tableIds IS NOT NULL) AND o.status IN :statuses ORDER BY o.createdAt DESC")
    List<Order> findByRestaurant_IdAndDiningTableIsNotNullAndStatusIn(
            @Param("restaurantId") Long restaurantId,
            @Param("statuses") List<OrderStatus> statuses);

    // Financial reports: Find orders with payments for revenue calculation
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.payments WHERE o.restaurant.id = :restaurantId AND o.createdAt BETWEEN :startDate AND :endDate ORDER BY o.createdAt DESC")
    List<Order> findByRestaurant_IdAndCreatedAtBetweenWithPaymentsOrderByCreatedAtDesc(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);

    // Daily statistics for restaurant
    @Query("SELECT COUNT(o), COALESCE(SUM(o.total), 0) FROM Order o WHERE o.restaurant.id = :restaurantId AND o.createdAt BETWEEN :startDate AND :endDate AND o.status NOT IN ('CANCELLED', 'REJECTED')")
    Object[] getDailyStatsForRestaurant(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);

    // ==================== SOFT DELETE QUERIES ====================

    /**
     * Find order by ID, excluding soft-deleted records
     */
    @Query("SELECT o FROM Order o WHERE o.id = :id AND o.deletedAt IS NULL")
    Optional<Order> findActiveById(@Param("id") Long id);

    /**
     * Find orders by restaurant and status, excluding soft-deleted records
     */
    @Query("SELECT o FROM Order o WHERE o.restaurant.id = :restaurantId AND o.status = :status AND o.deletedAt IS NULL ORDER BY o.createdAt DESC")
    List<Order> findActiveByRestaurantIdAndStatus(@Param("restaurantId") Long restaurantId, @Param("status") OrderStatus status);

    /**
     * Find soft-deleted orders for a restaurant (for audit/recovery)
     */
    @Query("SELECT o FROM Order o WHERE o.restaurant.id = :restaurantId AND o.deletedAt IS NOT NULL ORDER BY o.deletedAt DESC")
    List<Order> findDeletedByRestaurantId(@Param("restaurantId") Long restaurantId);

    /**
     * Find all orders including soft-deleted (for complete audit trail)
     */
    @Query("SELECT o FROM Order o WHERE o.restaurant.id = :restaurantId ORDER BY o.createdAt DESC")
    List<Order> findAllIncludingDeletedByRestaurantId(@Param("restaurantId") Long restaurantId);

    /**
     * Count active (non-deleted) orders by restaurant and status
     */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.restaurant.id = :restaurantId AND o.status = :status AND o.deletedAt IS NULL")
    long countActiveByRestaurantIdAndStatus(@Param("restaurantId") Long restaurantId, @Param("status") OrderStatus status);

    // ==================== TIMEZONE-AWARE QUERIES ====================

    /**
     * Find orders created after a specific time (timezone-aware)
     */
    @Query("SELECT o FROM Order o WHERE o.restaurant.id = :restaurantId AND o.createdAt > :since AND o.deletedAt IS NULL ORDER BY o.createdAt DESC")
    List<Order> findByRestaurantIdAndCreatedAtAfter(@Param("restaurantId") Long restaurantId, @Param("since") OffsetDateTime since);

    /**
     * Find orders by restaurant and status list (timezone-aware, excluding deleted)
     */
    @Query("SELECT o FROM Order o WHERE o.restaurant.id = :restaurantId AND o.status IN :statuses AND o.deletedAt IS NULL ORDER BY o.createdAt DESC")
    List<Order> findByRestaurantIdAndStatusIn(@Param("restaurantId") Long restaurantId, @Param("statuses") List<OrderStatus> statuses);

    /**
     * Find recent orders by customer phone (for order tracking)
     */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items " +
           "WHERE o.customer.phone = :phone AND o.createdAt >= :since ORDER BY o.createdAt DESC")
    List<Order> findByCustomerPhoneAndCreatedAtAfterWithDetails(
            @Param("phone") String phone,
            @Param("since") OffsetDateTime since);

    /**
     * Find orders by date range with items (for analytics)
     */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.createdAt >= :startDate AND o.createdAt <= :endDate ORDER BY o.createdAt DESC")
    List<Order> findByCreatedAtBetweenWithItemsOrderByCreatedAtDesc(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);

    /**
     * Find orders by source (for the platform external-orders page). No {@code JOIN FETCH o.items}:
     * a collection fetch with {@code Pageable} pages in memory (HHH90003004) — and this variant is
     * cross-tenant, so it would materialise every external order on the platform to serve one page.
     * The payload is batch-initialised by {@code OrderJsonHydration} in the service.
     */
    @Query("SELECT o FROM Order o WHERE o.orderSource IN :sources AND o.deletedAt IS NULL ORDER BY o.createdAt DESC")
    Page<Order> findByOrderSourceIn(@Param("sources") List<OrderSource> sources, Pageable pageable);

    /**
     * Find orders by restaurant and source (for external orders page). Same no-collection-fetch rule
     * as {@link #findByOrderSourceIn}.
     */
    @Query("SELECT o FROM Order o WHERE o.restaurant.id = :restaurantId AND o.orderSource IN :sources AND o.deletedAt IS NULL ORDER BY o.createdAt DESC")
    Page<Order> findByRestaurantIdAndOrderSourceIn(@Param("restaurantId") Long restaurantId, @Param("sources") List<OrderSource> sources, Pageable pageable);
}
