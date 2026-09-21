package com.elcafe.modules.inventory.repository;

import com.elcafe.modules.inventory.entity.InventoryTransaction;
import com.elcafe.modules.inventory.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    List<InventoryTransaction> findByIngredientId(Long ingredientId);

    List<InventoryTransaction> findByIngredientIdOrderByCreatedAtDesc(Long ingredientId);

    List<InventoryTransaction> findByType(TransactionType type);

    List<InventoryTransaction> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);

    @Query("SELECT it FROM InventoryTransaction it WHERE it.ingredient.id = :ingredientId AND it.createdAt BETWEEN :startDate AND :endDate ORDER BY it.createdAt DESC")
    List<InventoryTransaction> findByIngredientAndDateRange(Long ingredientId, LocalDateTime startDate, LocalDateTime endDate);

    @Query("SELECT it FROM InventoryTransaction it WHERE it.ingredient.restaurant.id = :restaurantId AND it.createdAt BETWEEN :startDate AND :endDate ORDER BY it.createdAt DESC")
    List<InventoryTransaction> findByRestaurantAndDateRange(Long restaurantId, LocalDateTime startDate, LocalDateTime endDate);
}
