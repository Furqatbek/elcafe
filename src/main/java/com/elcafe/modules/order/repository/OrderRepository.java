package com.elcafe.modules.order.repository;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
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
