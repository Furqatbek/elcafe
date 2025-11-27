package com.elcafe.modules.order.repository;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

    @Override
    @EntityGraph(value = "Order.full", type = EntityGraph.EntityGraphType.LOAD)
    Page<Order> findAll(Pageable pageable);

    @Override
    @EntityGraph(value = "Order.full", type = EntityGraph.EntityGraphType.LOAD)
    Optional<Order> findById(Long id);

    @EntityGraph(value = "Order.full", type = EntityGraph.EntityGraphType.LOAD)
    Optional<Order> findByOrderNumber(String orderNumber);

    @EntityGraph(value = "Order.full", type = EntityGraph.EntityGraphType.LOAD)
    List<Order> findByRestaurantIdAndStatusOrderByCreatedAtDesc(Long restaurantId, OrderStatus status);

    @EntityGraph(value = "Order.full", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT o FROM Order o " +
           "WHERE o.restaurant.id = :restaurantId " +
           "AND o.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY o.createdAt DESC")
    List<Order> findByRestaurantIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            @Param("restaurantId") Long restaurantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @EntityGraph(value = "Order.full", type = EntityGraph.EntityGraphType.LOAD)
    List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    @EntityGraph(value = "Order.full", type = EntityGraph.EntityGraphType.LOAD)
    List<Order> findByStatusOrderByCreatedAtAsc(OrderStatus status);

    @EntityGraph(value = "Order.full", type = EntityGraph.EntityGraphType.LOAD)
    List<Order> findByStatus(OrderStatus status);

    @EntityGraph(value = "Order.full", type = EntityGraph.EntityGraphType.LOAD)
    List<Order> findByRestaurantIdAndStatus(Long restaurantId, OrderStatus status);

    @Query("SELECT o FROM Order o WHERE o.deliveryInfo.courierId = :courierId ORDER BY o.createdAt DESC")
    List<Order> findByCourierId(@Param("courierId") Long courierId);

    // Customer Activity Queries
    @Query("SELECT COUNT(o) FROM Order o WHERE o.customer.id = :customerId")
    Long countByCustomerId(@Param("customerId") Long customerId);

    @Query("SELECT SUM(o.total) FROM Order o WHERE o.customer.id = :customerId")
    BigDecimal sumTotalByCustomerId(@Param("customerId") Long customerId);

    @Query("SELECT o FROM Order o WHERE o.customer.id = :customerId ORDER BY o.createdAt DESC")
    List<Order> findTopByCustomerIdOrderByCreatedAtDesc(@Param("customerId") Long customerId);

    @Query("SELECT DISTINCT o.orderSource FROM Order o WHERE o.customer.id = :customerId")
    List<OrderSource> findDistinctOrderSourcesByCustomerId(@Param("customerId") Long customerId);
}
