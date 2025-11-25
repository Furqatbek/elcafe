package com.elcafe.modules.kitchen.repository;

import com.elcafe.modules.kitchen.entity.KitchenOrder;
import com.elcafe.modules.kitchen.enums.KitchenOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KitchenOrderRepository extends JpaRepository<KitchenOrder, Long> {

    Optional<KitchenOrder> findByOrderId(Long orderId);

    List<KitchenOrder> findByStatusOrderByCreatedAtAsc(KitchenOrderStatus status);

    List<KitchenOrder> findByStatusInOrderByPriorityDescCreatedAtAsc(List<KitchenOrderStatus> statuses);

    @Query("SELECT ko FROM KitchenOrder ko WHERE ko.order.restaurant.id = :restaurantId AND ko.status IN :statuses ORDER BY ko.priority DESC, ko.createdAt ASC")
    List<KitchenOrder> findByRestaurantAndStatuses(@Param("restaurantId") Long restaurantId, @Param("statuses") List<KitchenOrderStatus> statuses);

    List<KitchenOrder> findByAssignedChef(String chefName);
}
