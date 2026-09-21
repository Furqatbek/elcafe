package com.elcafe.modules.menu.repository;

import com.elcafe.modules.menu.entity.ProductIngredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for ProductIngredient entity
 */
@Repository
public interface ProductIngredientRepository extends JpaRepository<ProductIngredient, Long> {

    List<ProductIngredient> findByProductId(Long productId);

    List<ProductIngredient> findByIngredientId(Long ingredientId);

    @Query("SELECT pi FROM ProductIngredient pi WHERE pi.product.id = :productId")
    List<ProductIngredient> findProductIngredientsWithDetails(@Param("productId") Long productId);

    void deleteByProductIdAndIngredientId(Long productId, Long ingredientId);
}
