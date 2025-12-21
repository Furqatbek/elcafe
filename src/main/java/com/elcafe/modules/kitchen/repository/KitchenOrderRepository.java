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

    @Query("SELECT DISTINCT ko FROM KitchenOrder ko " +
           "LEFT JOIN FETCH ko.order o " +
           "LEFT JOIN FETCH o.items " +
           "LEFT JOIN FETCH o.diningTable " +
           "WHERE ko.status = :status " +
           "ORDER BY ko.createdAt ASC")
    List<KitchenOrder> findByStatusOrderByCreatedAtAsc(@Param("status") KitchenOrderStatus status);

    @Query("SELECT DISTINCT ko FROM KitchenOrder ko " +
           "LEFT JOIN FETCH ko.order o " +
           "LEFT JOIN FETCH o.items " +
           "LEFT JOIN FETCH o.diningTable " +
           "WHERE ko.status IN :statuses " +
           "ORDER BY ko.priority DESC, ko.createdAt ASC")
    List<KitchenOrder> findByStatusInOrderByPriorityDescCreatedAtAsc(@Param("statuses") List<KitchenOrderStatus> statuses);

    @Query("SELECT DISTINCT ko FROM KitchenOrder ko " +
           "LEFT JOIN FETCH ko.order o " +
           "LEFT JOIN FETCH o.items " +
           "LEFT JOIN FETCH o.diningTable " +
           "WHERE o.restaurant.id = :restaurantId AND ko.status IN :statuses " +
           "ORDER BY ko.priority DESC, ko.createdAt ASC")
    List<KitchenOrder> findByRestaurantAndStatuses(@Param("restaurantId") Long restaurantId, @Param("statuses") List<KitchenOrderStatus> statuses);

    List<KitchenOrder> findByAssignedChef(String chefName);

    // Analytics queries
    @Query("SELECT ko FROM KitchenOrder ko WHERE ko.createdAt BETWEEN :startDate AND :endDate")
    List<KitchenOrder> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT ko FROM KitchenOrder ko WHERE ko.order.restaurant.id = :restaurantId AND ko.createdAt BETWEEN :startDate AND :endDate")
    List<KitchenOrder> findByRestaurantAndCreatedAtBetween(@Param("restaurantId") Long restaurantId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT ko FROM KitchenOrder ko WHERE ko.assignedChef = :chefName AND ko.createdAt BETWEEN :startDate AND :endDate")
    List<KitchenOrder> findByAssignedChefAndCreatedAtBetween(@Param("chefName") String chefName, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @Query("SELECT COUNT(ko) FROM KitchenOrder ko WHERE ko.status = :status")
    Long countByStatus(@Param("status") KitchenOrderStatus status);

    @Query("SELECT AVG(ko.actualPreparationTimeMinutes) FROM KitchenOrder ko WHERE ko.status IN :completedStatuses AND ko.actualPreparationTimeMinutes IS NOT NULL")
    Double getAveragePreparationTime(@Param("completedStatuses") List<KitchenOrderStatus> completedStatuses);
}
