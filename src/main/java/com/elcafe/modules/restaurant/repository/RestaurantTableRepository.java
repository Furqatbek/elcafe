package com.elcafe.modules.restaurant.repository;

import com.elcafe.modules.restaurant.entity.RestaurantTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {

    List<RestaurantTable> findByRestaurantId(Long restaurantId);

    List<RestaurantTable> findByRestaurantIdAndActive(Long restaurantId, Boolean active);

    List<RestaurantTable> findByRestaurantIdAndStatus(Long restaurantId, RestaurantTable.TableStatus status);

    List<RestaurantTable> findByRestaurantIdAndSection(Long restaurantId, String section);

    Optional<RestaurantTable> findByRestaurantIdAndTableNumber(Long restaurantId, String tableNumber);

    @Query("SELECT DISTINCT t.section FROM RestaurantTable t WHERE t.restaurant.id = :restaurantId AND t.section IS NOT NULL")
    List<String> findDistinctSectionsByRestaurantId(Long restaurantId);

    @Query("SELECT COUNT(t) FROM RestaurantTable t WHERE t.restaurant.id = :restaurantId AND t.status = :status")
    Long countByRestaurantIdAndStatus(Long restaurantId, RestaurantTable.TableStatus status);

    void deleteByRestaurantId(Long restaurantId);
}
