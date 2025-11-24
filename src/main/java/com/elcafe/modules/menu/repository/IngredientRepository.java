package com.elcafe.modules.menu.repository;

import com.elcafe.modules.menu.entity.Ingredient;
import com.elcafe.modules.menu.enums.IngredientCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Ingredient entity
 */
@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    Optional<Ingredient> findByName(String name);

    Page<Ingredient> findByIsActive(Boolean isActive, Pageable pageable);

    Page<Ingredient> findByCategory(IngredientCategory category, Pageable pageable);

    @Query("SELECT i FROM Ingredient i WHERE i.isActive = true AND i.currentStock <= i.minimumStock")
    List<Ingredient> findLowStockIngredients();

    @Query("SELECT i FROM Ingredient i WHERE i.isActive = :isActive AND " +
           "(LOWER(i.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(i.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Ingredient> searchIngredients(
            @Param("search") String search,
            @Param("isActive") Boolean isActive,
            Pageable pageable
    );

    boolean existsByName(String name);
}
