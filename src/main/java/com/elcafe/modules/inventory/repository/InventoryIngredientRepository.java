package com.elcafe.modules.inventory.repository;

import com.elcafe.modules.inventory.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryIngredientRepository extends JpaRepository<Ingredient, Long> {

    List<Ingredient> findByRestaurantId(Long restaurantId);

    List<Ingredient> findByRestaurantIdAndActiveTrue(Long restaurantId);

    Optional<Ingredient> findByRestaurantIdAndName(Long restaurantId, String name);

    @Query("SELECT i FROM InventoryIngredient i WHERE i.restaurant.id = :restaurantId AND i.currentStock <= i.minimumStock AND i.active = true")
    List<Ingredient> findLowStockIngredients(Long restaurantId);

    @Query("SELECT i FROM InventoryIngredient i WHERE i.restaurant.id = :restaurantId AND i.currentStock <= i.reorderLevel AND i.active = true")
    List<Ingredient> findIngredientsNeedingReorder(Long restaurantId);

    @Query("SELECT i FROM InventoryIngredient i WHERE i.restaurant.id = :restaurantId AND i.trackInventory = true AND i.active = true")
    List<Ingredient> findTrackedIngredients(Long restaurantId);
}
