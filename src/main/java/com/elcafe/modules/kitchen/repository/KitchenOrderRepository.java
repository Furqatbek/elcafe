package com.elcafe.modules.kitchen.repository;

import com.elcafe.modules.kitchen.entity.KitchenOrder;
import com.elcafe.modules.kitchen.enums.KitchenOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface KitchenOrderRepository extends JpaRepository<KitchenOrder, Long> {

    Optional<KitchenOrder> findByOrderId(Long orderId);

    /**
     * Find kitchen order by ID with Order eagerly fetched to prevent LazyInitializationException.
     */
    @Query("SELECT ko FROM KitchenOrder ko JOIN FETCH ko.order WHERE ko.id = :id")
    Optional<KitchenOrder> findByIdWithOrder(@Param("id") Long id);

    /**
     * Find kitchen order by ID with Order and Restaurant eagerly fetched.
     */
    @Query("SELECT ko FROM KitchenOrder ko JOIN FETCH ko.order o LEFT JOIN FETCH o.restaurant WHERE ko.id = :id")
    Optional<KitchenOrder> findByIdWithOrderAndRestaurant(@Param("id") Long id);

    List<KitchenOrder> findByStatusOrderByCreatedAtAsc(KitchenOrderStatus status);

    List<KitchenOrder> findByStatusInOrderByPriorityDescCreatedAtAsc(List<KitchenOrderStatus> statuses);

    @Query("SELECT ko FROM KitchenOrder ko JOIN FETCH ko.order o JOIN FETCH o.restaurant r WHERE r.id = :restaurantId AND ko.status IN :statuses ORDER BY ko.priority DESC, ko.createdAt ASC")
    List<KitchenOrder> findByRestaurantAndStatuses(@Param("restaurantId") Long restaurantId, @Param("statuses") List<KitchenOrderStatus> statuses);

    List<KitchenOrder> findByAssignedChef(String chefName);

    // Analytics queries
    @Query("SELECT ko FROM KitchenOrder ko WHERE ko.createdAt BETWEEN :startDate AND :endDate")
    List<KitchenOrder> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT ko FROM KitchenOrder ko JOIN FETCH ko.order o JOIN FETCH o.restaurant r WHERE r.id = :restaurantId AND ko.createdAt BETWEEN :startDate AND :endDate")
    List<KitchenOrder> findByRestaurantAndCreatedAtBetween(@Param("restaurantId") Long restaurantId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT ko FROM KitchenOrder ko WHERE ko.assignedChef = :chefName AND ko.createdAt BETWEEN :startDate AND :endDate")
    List<KitchenOrder> findByAssignedChefAndCreatedAtBetween(@Param("chefName") String chefName, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(ko) FROM KitchenOrder ko WHERE ko.status = :status")
    Long countByStatus(@Param("status") KitchenOrderStatus status);

    @Query("SELECT AVG(ko.actualPreparationTimeMinutes) FROM KitchenOrder ko WHERE ko.status IN :completedStatuses AND ko.actualPreparationTimeMinutes IS NOT NULL")
    Double getAveragePreparationTime(@Param("completedStatuses") List<KitchenOrderStatus> completedStatuses);

    /**
     * Find orders stuck in a specific status since before the given cutoff time.
     * Used for timeout detection and alerting.
     */
    @Query("SELECT ko FROM KitchenOrder ko JOIN FETCH ko.order o JOIN FETCH o.restaurant r " +
           "WHERE r.id = :restaurantId AND ko.status = :status " +
           "AND ((ko.status = 'PREPARING' AND ko.preparationStartedAt < :cutoff) " +
           "OR (ko.status = 'READY' AND ko.preparationCompletedAt < :cutoff)) " +
           "ORDER BY ko.createdAt ASC")
    List<KitchenOrder> findStuckOrders(
            @Param("restaurantId") Long restaurantId,
            @Param("status") KitchenOrderStatus status,
            @Param("cutoff") LocalDateTime cutoff);

    /**
     * Find all stuck orders across all restaurants for system-wide monitoring.
     */
    @Query("SELECT ko FROM KitchenOrder ko JOIN FETCH ko.order o JOIN FETCH o.restaurant r " +
           "WHERE ko.status = :status " +
           "AND ((ko.status = 'PREPARING' AND ko.preparationStartedAt < :cutoff) " +
           "OR (ko.status = 'READY' AND ko.preparationCompletedAt < :cutoff)) " +
           "ORDER BY ko.createdAt ASC")
    List<KitchenOrder> findAllStuckOrders(
            @Param("status") KitchenOrderStatus status,
            @Param("cutoff") LocalDateTime cutoff);
}
