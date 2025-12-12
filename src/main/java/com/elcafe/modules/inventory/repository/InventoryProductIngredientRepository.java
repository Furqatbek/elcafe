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

    @Query("SELECT pi FROM ProductIngredient pi JOIN FETCH pi.ingredient WHERE pi.product.id = :productId")
    List<ProductIngredient> findByProductIdWithIngredients(Long productId);

    void deleteByProductId(Long productId);

    void deleteByProductIdAndIngredientId(Long productId, Long ingredientId);
}
