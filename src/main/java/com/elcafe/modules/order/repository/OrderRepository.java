package com.elcafe.modules.order.repository;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
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
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    @Override
    @EntityGraph(value = "Order.withItems", type = EntityGraph.EntityGraphType.FETCH)
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);

    @Override
    @EntityGraph(value = "Order.withItems", type = EntityGraph.EntityGraphType.FETCH)
    List<Order> findAll(Specification<Order> spec);

    @Override
    @EntityGraph(value = "Order.withItems", type = EntityGraph.EntityGraphType.FETCH)
    Optional<Order> findById(Long id);

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

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.customer.id = :customerId ORDER BY o.createdAt DESC")
    List<Order> findByCustomer_IdWithItemsOrderByCreatedAtDesc(@Param("customerId") Long customerId);

    List<Order> findByStatusOrderByCreatedAtAsc(OrderStatus status);

    List<Order> findByWaiterAndStatusInOrderByCreatedAtDesc(Waiter waiter, List<OrderStatus> statuses);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.waiter = :waiter AND o.status IN :statuses ORDER BY o.createdAt DESC")
    List<Order> findByWaiterAndStatusInWithItemsOrderByCreatedAtDesc(@Param("waiter") Waiter waiter, @Param("statuses") List<OrderStatus> statuses);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.waiter = :waiter ORDER BY o.createdAt DESC")
    List<Order> findByWaiterWithItemsOrderByCreatedAtDesc(@Param("waiter") Waiter waiter);

    List<Order> findByRestaurant_IdAndStatus(Long restaurantId, OrderStatus status);

    // Find active orders by table
    List<Order> findByDiningTable_IdAndStatusIn(Long tableId, List<OrderStatus> statuses);

    List<Order> findByStatus(OrderStatus status);

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
     * Find orders by date range (for analytics when restaurantId is null)
     */
    @Query("SELECT o FROM Order o WHERE o.createdAt >= :startDate AND o.createdAt <= :endDate ORDER BY o.createdAt DESC")
    List<Order> findByCreatedAtBetweenOrderByCreatedAtDesc(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);

    /**
     * Find orders by date range with items (for analytics)
     */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.createdAt >= :startDate AND o.createdAt <= :endDate ORDER BY o.createdAt DESC")
    List<Order> findByCreatedAtBetweenWithItemsOrderByCreatedAtDesc(
            @Param("startDate") OffsetDateTime startDate,
            @Param("endDate") OffsetDateTime endDate);

    /**
     * Find orders by source (for external orders page)
     */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.orderSource IN :sources AND o.deletedAt IS NULL ORDER BY o.createdAt DESC")
    Page<Order> findByOrderSourceIn(@Param("sources") List<OrderSource> sources, Pageable pageable);

    /**
     * Find orders by restaurant and source (for external orders page)
     */
    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.restaurant.id = :restaurantId AND o.orderSource IN :sources AND o.deletedAt IS NULL ORDER BY o.createdAt DESC")
    Page<Order> findByRestaurantIdAndOrderSourceIn(@Param("restaurantId") Long restaurantId, @Param("sources") List<OrderSource> sources, Pageable pageable);
}
