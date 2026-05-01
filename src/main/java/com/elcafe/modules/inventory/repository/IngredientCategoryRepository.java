package com.elcafe.modules.inventory.repository;

import com.elcafe.modules.inventory.entity.IngredientCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IngredientCategoryRepository extends JpaRepository<IngredientCategory, Long> {

    List<IngredientCategory> findByRestaurantIdOrderBySortOrderAscNameAsc(Long restaurantId);

    boolean existsByRestaurantIdAndName(Long restaurantId, String name);
}
