package com.elcafe.modules.menu.repository;

import com.elcafe.modules.menu.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByRestaurant_IdAndActiveTrueOrderBySortOrder(Long restaurantId);

    @Query("SELECT c FROM Category c WHERE c.restaurant.id = :restaurantId AND c.active = true ORDER BY c.sortOrder")
    List<Category> findByRestaurantIdAndActiveTrueOrderBySortOrder(@Param("restaurantId") Long restaurantId);

    List<Category> findByRestaurant_IdOrderBySortOrder(Long restaurantId);
}
