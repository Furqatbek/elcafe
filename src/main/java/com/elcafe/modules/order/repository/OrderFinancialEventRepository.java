package com.elcafe.modules.order.repository;

import com.elcafe.modules.order.entity.OrderFinancialEvent;
import com.elcafe.modules.order.entity.OrderFinancialEvent.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderFinancialEventRepository extends JpaRepository<OrderFinancialEvent, Long> {

    List<OrderFinancialEvent> findByOrderIdOrderBySequenceNumber(Long orderId);

    List<OrderFinancialEvent> findByOrderIdAndEventTypeOrderBySequenceNumber(Long orderId, EventType eventType);

    @Query("SELECT MAX(e.sequenceNumber) FROM OrderFinancialEvent e WHERE e.orderId = :orderId")
    Optional<Integer> getMaxSequenceNumber(@Param("orderId") Long orderId);

    @Query("SELECT e FROM OrderFinancialEvent e WHERE e.orderId = :orderId AND e.createdAt <= :timestamp ORDER BY e.sequenceNumber DESC")
    List<OrderFinancialEvent> getEventsUpToTimestamp(
            @Param("orderId") Long orderId,
            @Param("timestamp") LocalDateTime timestamp);

    @Query("SELECT e FROM OrderFinancialEvent e WHERE e.orderId = :orderId ORDER BY e.sequenceNumber DESC LIMIT 1")
    Optional<OrderFinancialEvent> getLatestEvent(@Param("orderId") Long orderId);

    List<OrderFinancialEvent> findByOrderIdAndEventTypeInOrderBySequenceNumber(
            Long orderId, List<EventType> eventTypes);

    @Query("SELECT e FROM OrderFinancialEvent e WHERE e.createdAt BETWEEN :startDate AND :endDate AND e.eventType IN :eventTypes ORDER BY e.createdAt")
    List<OrderFinancialEvent> findEventsByDateRangeAndTypes(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("eventTypes") List<EventType> eventTypes);

    @Query("SELECT e FROM OrderFinancialEvent e WHERE e.orderId = :orderId AND e.paymentId = :paymentId ORDER BY e.sequenceNumber")
    List<OrderFinancialEvent> findPaymentEvents(
            @Param("orderId") Long orderId,
            @Param("paymentId") Long paymentId);
}
