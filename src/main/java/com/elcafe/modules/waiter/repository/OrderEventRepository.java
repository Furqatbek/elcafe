package com.elcafe.modules.waiter.repository;

import com.elcafe.modules.waiter.entity.OrderEvent;
import com.elcafe.modules.waiter.enums.OrderEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {

    /**
     * Find all events for an order
     */
    List<OrderEvent> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    /**
     * Find events by event type
     */
    List<OrderEvent> findByEventTypeOrderByCreatedAtDesc(OrderEventType eventType);

    /**
     * Find events by who triggered them
     */
    List<OrderEvent> findByTriggeredByOrderByCreatedAtDesc(String triggeredBy);

    /**
     * Find events within date range
     */
    @Query("SELECT oe FROM OrderEvent oe " +
           "WHERE oe.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY oe.createdAt DESC")
    List<OrderEvent> findByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find events for a waiter within date range
     */
    @Query("SELECT oe FROM OrderEvent oe " +
           "WHERE oe.triggeredBy = :waiterName " +
           "AND oe.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY oe.createdAt DESC")
    List<OrderEvent> findByWaiterAndDateRange(
        @Param("waiterName") String waiterName,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find events by order ID and event type
     */
    List<OrderEvent> findByOrderIdAndEventType(Long orderId, OrderEventType eventType);

    /**
     * Find latest event for an order
     */
    @Query("SELECT oe FROM OrderEvent oe " +
           "WHERE oe.order.id = :orderId " +
           "ORDER BY oe.createdAt DESC " +
           "LIMIT 1")
    OrderEvent findLatestByOrderId(@Param("orderId") Long orderId);

    /**
     * Count events by type within date range
     */
    @Query("SELECT COUNT(oe) FROM OrderEvent oe " +
           "WHERE oe.eventType = :eventType " +
           "AND oe.createdAt BETWEEN :startDate AND :endDate")
    long countByEventTypeAndDateRange(
        @Param("eventType") OrderEventType eventType,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
}
