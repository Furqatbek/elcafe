package com.elcafe.modules.order.repository;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.waiter.entity.Waiter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByOrderNumber(String orderNumber);

    List<Order> findByRestaurantIdAndStatusOrderByCreatedAtDesc(Long restaurantId, OrderStatus status);

    List<Order> findByRestaurantIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long restaurantId,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    List<Order> findByStatusOrderByCreatedAtAsc(OrderStatus status);

    List<Order> findByWaiterAndStatusInOrderByCreatedAtDesc(Waiter waiter, List<OrderStatus> statuses);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.waiter = :waiter AND o.status IN :statuses ORDER BY o.createdAt DESC")
    List<Order> findByWaiterAndStatusInWithItemsOrderByCreatedAtDesc(@Param("waiter") Waiter waiter, @Param("statuses") List<OrderStatus> statuses);

    List<Order> findByRestaurantIdAndStatus(Long restaurantId, OrderStatus status);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByDeliveryInfo_CourierId(Long courierId);

    List<Order> findByStatusAndPlacedAtBefore(OrderStatus status, LocalDateTime placedAt);

    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime createdAt);

    List<Order> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

    Optional<Order> findByPaymentIntentId(String paymentIntentId);

    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE o.customer.id = :customerId")
    BigDecimal sumTotalByCustomerId(@Param("customerId") Long customerId);

    @Query("SELECT DISTINCT o.orderSource FROM Order o WHERE o.customer.id = :customerId")
    List<OrderSource> findDistinctOrderSourcesByCustomerId(@Param("customerId") Long customerId);

    // Waiter metrics queries
    @Query("SELECT COALESCE(SUM(o.total), 0) FROM Order o WHERE o.waiter.id = :waiterId AND o.status NOT IN ('PENDING', 'CANCELLED')")
    BigDecimal calculateTotalRevenueByWaiter(@Param("waiterId") Long waiterId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.waiter.id = :waiterId AND o.status NOT IN ('PENDING', 'CANCELLED')")
    Long countValidOrdersByWaiter(@Param("waiterId") Long waiterId);

    @Query("SELECT o FROM Order o WHERE o.waiter.id = :waiterId AND o.status NOT IN ('PENDING', 'CANCELLED') ORDER BY o.createdAt DESC")
    List<Order> findRecentOrdersByWaiter(@Param("waiterId") Long waiterId, Pageable pageable);

    @Query("SELECT CAST(o.createdAt AS LocalDate) as date, COALESCE(SUM(o.total), 0) as revenue, COUNT(o) as orderCount " +
           "FROM Order o " +
           "WHERE o.waiter.id = :waiterId " +
           "AND o.status NOT IN ('PENDING', 'CANCELLED') " +
           "AND o.createdAt >= :startDate " +
           "GROUP BY CAST(o.createdAt AS LocalDate) " +
           "ORDER BY CAST(o.createdAt AS LocalDate) DESC")
    List<Object[]> findDailyRevenueByWaiter(@Param("waiterId") Long waiterId, @Param("startDate") LocalDateTime startDate);
}
