package com.elcafe.modules.inventory.repository;

import com.elcafe.modules.inventory.entity.ProductIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryProductIngredientRepository extends JpaRepository<ProductIngredient, Long> {

    List<ProductIngredient> findByProductId(Long productId);

    List<ProductIngredient> findByIngredientId(Long ingredientId);

    Optional<ProductIngredient> findByProductIdAndIngredientId(Long productId, Long ingredientId);

    @Query("SELECT pi FROM InventoryProductIngredient pi JOIN FETCH pi.product JOIN FETCH pi.ingredient WHERE pi.product.id = :productId")
    List<ProductIngredient> findByProductIdWithIngredients(Long productId);

    @Query("SELECT pi FROM InventoryProductIngredient pi JOIN FETCH pi.product JOIN FETCH pi.ingredient WHERE pi.ingredient.id = :ingredientId")
    List<ProductIngredient> findByIngredientIdWithProductAndIngredient(Long ingredientId);

    @Query("SELECT pi FROM InventoryProductIngredient pi JOIN FETCH pi.product WHERE pi.ingredient.id = :ingredientId")
    List<ProductIngredient> findByIngredientIdWithProduct(Long ingredientId);

    @Query("SELECT pi FROM InventoryProductIngredient pi JOIN FETCH pi.product JOIN FETCH pi.ingredient WHERE pi.id = :id")
    Optional<ProductIngredient> findByIdWithProductAndIngredient(Long id);

    void deleteByProductId(Long productId);

    void deleteByProductIdAndIngredientId(Long productId, Long ingredientId);

    /**
     * Find all product ingredients with their products and ingredients (for bulk cost calculation)
     */
    @Query("SELECT pi FROM InventoryProductIngredient pi JOIN FETCH pi.product JOIN FETCH pi.ingredient")
    List<ProductIngredient> findAllWithProductsAndIngredients();

    /**
     * Find product ingredients for a list of product IDs (for batch operations)
     */
    @Query("SELECT pi FROM InventoryProductIngredient pi JOIN FETCH pi.ingredient WHERE pi.product.id IN :productIds")
    List<ProductIngredient> findByProductIdInWithIngredients(java.util.Collection<Long> productIds);
}
