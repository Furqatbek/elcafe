package com.elcafe.modules.financial.repository;

import com.elcafe.modules.financial.entity.PurchaseOrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItem, Long> {

    List<PurchaseOrderItem> findByPurchaseOrderId(Long purchaseOrderId);

    List<PurchaseOrderItem> findByIngredientId(Long ingredientId);

    @Query("SELECT poi FROM FinancialPurchaseOrderItem poi " +
           "WHERE poi.purchaseOrder.id = :purchaseOrderId " +
           "AND poi.receivedQuantity < poi.quantity")
    List<PurchaseOrderItem> findPartiallyReceivedItems(Long purchaseOrderId);

    @Query("SELECT poi FROM FinancialPurchaseOrderItem poi " +
           "JOIN FETCH poi.ingredient " +
           "WHERE poi.purchaseOrder.id = :purchaseOrderId")
    List<PurchaseOrderItem> findByPurchaseOrderIdWithIngredient(Long purchaseOrderId);
}
