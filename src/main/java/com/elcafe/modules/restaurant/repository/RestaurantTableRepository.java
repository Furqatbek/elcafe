package com.elcafe.modules.restaurant.repository;

import com.elcafe.modules.restaurant.entity.RestaurantTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {

    List<RestaurantTable> findByRestaurant_Id(Long restaurantId);

    List<RestaurantTable> findByRestaurant_IdAndActive(Long restaurantId, Boolean active);

    List<RestaurantTable> findByRestaurant_IdAndActiveTrue(Long restaurantId);

    List<RestaurantTable> findByRestaurant_IdAndStatus(Long restaurantId, RestaurantTable.TableStatus status);

    List<RestaurantTable> findByRestaurant_IdAndSection(Long restaurantId, String section);

    Optional<RestaurantTable> findByRestaurant_IdAndTableNumber(Long restaurantId, String tableNumber);

    @Query("SELECT DISTINCT t.section FROM RestaurantTable t WHERE t.restaurant.id = :restaurantId AND t.section IS NOT NULL")
    List<String> findDistinctSectionsByRestaurantId(Long restaurantId);

    @Query("SELECT COUNT(t) FROM RestaurantTable t WHERE t.restaurant.id = :restaurantId AND t.status = :status")
    Long countByRestaurantIdAndStatus(Long restaurantId, RestaurantTable.TableStatus status);

    List<RestaurantTable> findByMergedTable(RestaurantTable mergedTable);

    void deleteByRestaurantId(Long restaurantId);
}
