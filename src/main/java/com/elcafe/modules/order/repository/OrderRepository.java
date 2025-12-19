package com.elcafe.modules.order.repository;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.waiter.entity.Waiter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

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
}
