package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryTransaction;
import com.elcafe.modules.inventory.enums.TransactionType;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.InventoryTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Writes best-effort inventory audit rows in their own transaction.
 *
 * An audit row must never be able to fail the business operation that produced
 * it. Saving one inside the caller's transaction and catching the exception does
 * NOT achieve that: Spring has already marked the transaction rollback-only, so
 * the caller's commit then fails with UnexpectedRollbackException — the catch
 * block cannot actually swallow it. EmployeeConsumptionService documents the
 * same trap for payroll advances.
 *
 * REQUIRES_NEW suspends the caller's transaction and commits the audit row
 * independently, so a failure here is logged and forgotten without poisoning
 * the order (or consumption) that triggered it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryAuditService {

    private final InventoryTransactionRepository transactionRepository;
    private final InventoryIngredientRepository ingredientRepository;

    /**
     * Record a packaging deduction (cup, lid, bag) against an ingredient.
     * Takes ids and values rather than a managed entity, so nothing is carried
     * across the transaction boundary.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPackagingDeduction(Long ingredientId,
                                         BigDecimal quantity,
                                         BigDecimal balanceBefore,
                                         BigDecimal balanceAfter,
                                         BigDecimal unitCost,
                                         String forProductName) {
        try {
            Ingredient ingredient = ingredientRepository.findById(ingredientId).orElse(null);
            if (ingredient == null) {
                log.warn("Cannot record packaging audit row: ingredient {} not found", ingredientId);
                return;
            }
            BigDecimal cost = unitCost != null ? unitCost : BigDecimal.ZERO;

            transactionRepository.save(InventoryTransaction.builder()
                    .ingredient(ingredient)
                    .type(TransactionType.ORDER_DEDUCTION)
                    .quantity(quantity)
                    .balanceBefore(balanceBefore)
                    .balanceAfter(balanceAfter)
                    .referenceType("PACKAGING")
                    .notes("Packaging for " + forProductName)
                    .performedBy("SYSTEM")
                    .costPerUnit(cost)
                    .totalCost(cost.multiply(quantity))
                    .build());
        } catch (Exception e) {
            // Isolated transaction — safe to swallow without affecting the caller.
            log.warn("Failed to record packaging audit row for ingredient {}: {}",
                    ingredientId, e.getMessage());
        }
    }
}
