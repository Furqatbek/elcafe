package com.elcafe.modules.kitchen.repository;

import com.elcafe.modules.kitchen.entity.KitchenStation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface KitchenStationRepository extends JpaRepository<KitchenStation, Long> {

    List<KitchenStation> findByRestaurant_IdOrderBySortOrder(Long restaurantId);

    List<KitchenStation> findByRestaurant_IdAndActiveTrueOrderBySortOrder(Long restaurantId);

    Optional<KitchenStation> findByIdAndRestaurant_Id(Long id, Long restaurantId);

    boolean existsByNameAndRestaurant_Id(String name, Long restaurantId);

    boolean existsByNameAndRestaurant_IdAndIdNot(String name, Long restaurantId, Long id);

    @Query("SELECT ks FROM KitchenStation ks LEFT JOIN FETCH ks.printer WHERE ks.restaurant.id = :restaurantId AND ks.active = true ORDER BY ks.sortOrder")
    List<KitchenStation> findActiveStationsWithPrinters(@Param("restaurantId") Long restaurantId);

    @Query("SELECT ks FROM KitchenStation ks LEFT JOIN FETCH ks.printer WHERE ks.id = :id")
    Optional<KitchenStation> findByIdWithPrinter(@Param("id") Long id);
}
