package com.elcafe.modules.selfservice.repository;

import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.selfservice.entity.SelfServiceOrder;
import com.elcafe.modules.selfservice.enums.SelfServiceOrderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SelfServiceOrderRepository extends JpaRepository<SelfServiceOrder, Long> {

    /** Terminal order statuses that indicate an order is no longer pending. */
    List<OrderStatus> TERMINAL_STATUSES = List.of(OrderStatus.CANCELLED, OrderStatus.COMPLETED);

    Optional<SelfServiceOrder> findByOrderId(Long orderId);

    List<SelfServiceOrder> findBySessionId(Long sessionId);

    Page<SelfServiceOrder> findByOrderRestaurantIdOrderByCreatedAtDesc(Long restaurantId, Pageable pageable);

    @Query("SELECT o FROM SelfServiceOrder o WHERE o.order.restaurant.id = :restaurantId " +
           "AND o.actualReadyTime IS NULL AND o.order.status NOT IN :terminalStatuses")
    List<SelfServiceOrder> findPendingByRestaurant(
            @Param("restaurantId") Long restaurantId,
            @Param("terminalStatuses") List<OrderStatus> terminalStatuses);

    /**
     * Find pending orders for a restaurant using default terminal statuses.
     */
    default List<SelfServiceOrder> findPendingByRestaurant(Long restaurantId) {
        return findPendingByRestaurant(restaurantId, TERMINAL_STATUSES);
    }

    @Query("SELECT o FROM SelfServiceOrder o WHERE o.actualReadyTime IS NOT NULL " +
           "AND o.pickedUpAt IS NULL AND o.order.restaurant.id = :restaurantId")
    List<SelfServiceOrder> findReadyForPickup(@Param("restaurantId") Long restaurantId);

    @Query("SELECT COUNT(o) FROM SelfServiceOrder o WHERE o.order.restaurant.id = :restaurantId " +
           "AND o.createdAt BETWEEN :start AND :end")
    long countByRestaurantAndDateRange(
            @Param("restaurantId") Long restaurantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT o.orderType, COUNT(o) FROM SelfServiceOrder o " +
           "WHERE o.order.restaurant.id = :restaurantId GROUP BY o.orderType")
    List<Object[]> countByOrderType(@Param("restaurantId") Long restaurantId);
}
