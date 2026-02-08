package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryBatch;
import com.elcafe.modules.inventory.entity.InventoryTransaction;
import com.elcafe.modules.inventory.enums.TransactionType;
import com.elcafe.modules.inventory.repository.InventoryBatchRepository;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.InventoryTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Centralized service for all stock modification operations.
 * This service ensures thread-safe stock modifications with optimistic locking
 * and retry logic to prevent race conditions and lost updates.
 *
 * ALL stock modifications should go through this service to maintain consistency
 * between Ingredient.currentStock and InventoryBatch quantities.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockOperationService {

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 50;

    private final InventoryIngredientRepository ingredientRepository;
    private final InventoryBatchRepository batchRepository;
    private final InventoryTransactionRepository transactionRepository;

    /**
     * Add stock to an ingredient with retry logic for concurrent access.
     *
     * @param ingredientId the ingredient to add stock to
     * @param quantity the quantity to add
     * @param transactionType the type of transaction (PURCHASE, ADJUSTMENT, etc.)
     * @param referenceType optional reference type (e.g., "PURCHASE_ORDER")
     * @param referenceId optional reference ID
     * @param notes optional notes
     * @param performedBy who performed the operation
     * @return the updated ingredient
     * @throws StockOperationException if operation fails after retries
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Ingredient addStockWithRetry(Long ingredientId, BigDecimal quantity,
                                         TransactionType transactionType,
                                         String referenceType, Long referenceId,
                                         String notes, String performedBy) {
        return executeWithRetry(() -> {
            Ingredient ingredient = ingredientRepository.findById(ingredientId)
                    .orElseThrow(() -> new RuntimeException("Ingredient not found: " + ingredientId));

            BigDecimal balanceBefore = ingredient.getCurrentStock();
            ingredient.addStock(quantity);
            Ingredient savedIngredient = ingredientRepository.save(ingredient);
            BigDecimal balanceAfter = savedIngredient.getCurrentStock();

            // Record transaction
            InventoryTransaction transaction = InventoryTransaction.builder()
                    .ingredient(savedIngredient)
                    .type(transactionType)
                    .quantity(quantity)
                    .balanceBefore(balanceBefore)
                    .balanceAfter(balanceAfter)
                    .referenceType(referenceType)
                    .referenceId(referenceId)
                    .notes(notes)
                    .performedBy(performedBy)
                    .build();
            transactionRepository.save(transaction);

            log.info("Stock added: {} {} of {} (version: {})",
                    quantity, savedIngredient.getUnit(), savedIngredient.getName(),
                    savedIngredient.getVersion());

            return savedIngredient;
        }, "addStock", ingredientId);
    }

    /**
     * Deduct stock from an ingredient with retry logic for concurrent access.
     *
     * @param ingredientId the ingredient to deduct stock from
     * @param quantity the quantity to deduct
     * @param transactionType the type of transaction
     * @param referenceType optional reference type
     * @param referenceId optional reference ID
     * @param notes optional notes
     * @param performedBy who performed the operation
     * @return the updated ingredient
     * @throws StockOperationException if operation fails after retries
     * @throws InsufficientStockException if there's not enough stock
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Ingredient deductStockWithRetry(Long ingredientId, BigDecimal quantity,
                                            TransactionType transactionType,
                                            String referenceType, Long referenceId,
                                            String notes, String performedBy) {
        return executeWithRetry(() -> {
            Ingredient ingredient = ingredientRepository.findById(ingredientId)
                    .orElseThrow(() -> new RuntimeException("Ingredient not found: " + ingredientId));

            BigDecimal balanceBefore = ingredient.getCurrentStock();

            // Check stock availability before attempting deduction
            if (!ingredient.hasStock(quantity)) {
                throw new InsufficientStockException(
                        String.format("Insufficient stock for %s: required %s, available %s",
                                ingredient.getName(), quantity, ingredient.getCurrentStock()));
            }

            ingredient.deductStock(quantity);
            Ingredient savedIngredient = ingredientRepository.save(ingredient);
            BigDecimal balanceAfter = savedIngredient.getCurrentStock();

            // Record transaction
            InventoryTransaction transaction = InventoryTransaction.builder()
                    .ingredient(savedIngredient)
                    .type(transactionType)
                    .quantity(quantity)
                    .balanceBefore(balanceBefore)
                    .balanceAfter(balanceAfter)
                    .referenceType(referenceType)
                    .referenceId(referenceId)
                    .notes(notes)
                    .performedBy(performedBy)
                    .build();
            transactionRepository.save(transaction);

            log.info("Stock deducted: {} {} of {} (version: {})",
                    quantity, savedIngredient.getUnit(), savedIngredient.getName(),
                    savedIngredient.getVersion());

            return savedIngredient;
        }, "deductStock", ingredientId);
    }

    /**
     * Force deduct stock (allows going to zero, e.g., for expired batch write-offs).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Ingredient forceDeductStockWithRetry(Long ingredientId, BigDecimal quantity,
                                                 TransactionType transactionType,
                                                 String referenceType, Long referenceId,
                                                 String notes, String performedBy) {
        return executeWithRetry(() -> {
            Ingredient ingredient = ingredientRepository.findById(ingredientId)
                    .orElseThrow(() -> new RuntimeException("Ingredient not found: " + ingredientId));

            BigDecimal balanceBefore = ingredient.getCurrentStock();
            ingredient.forceDeductStock(quantity);
            Ingredient savedIngredient = ingredientRepository.save(ingredient);
            BigDecimal balanceAfter = savedIngredient.getCurrentStock();

            // Record transaction
            InventoryTransaction transaction = InventoryTransaction.builder()
                    .ingredient(savedIngredient)
                    .type(transactionType)
                    .quantity(quantity)
                    .balanceBefore(balanceBefore)
                    .balanceAfter(balanceAfter)
                    .referenceType(referenceType)
                    .referenceId(referenceId)
                    .notes(notes)
                    .performedBy(performedBy)
                    .build();
            transactionRepository.save(transaction);

            log.info("Stock force deducted: {} {} of {} (version: {})",
                    quantity, savedIngredient.getUnit(), savedIngredient.getName(),
                    savedIngredient.getVersion());

            return savedIngredient;
        }, "forceDeductStock", ingredientId);
    }

    /**
     * Reconcile stock between Ingredient.currentStock and sum of InventoryBatch quantities.
     * Returns discrepancy details if found.
     */
    @Transactional(readOnly = true)
    public StockReconciliationResult reconcileStock(Long ingredientId) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new RuntimeException("Ingredient not found: " + ingredientId));

        // Only reconcile if expiry tracking is enabled (batches are used)
        if (!ingredient.getTrackExpiry()) {
            return new StockReconciliationResult(ingredientId, ingredient.getName(),
                    ingredient.getCurrentStock(), ingredient.getCurrentStock(),
                    BigDecimal.ZERO, true, "Expiry tracking disabled - no batch reconciliation needed");
        }

        BigDecimal batchTotal = batchRepository.getEffectiveQuantity(ingredientId, LocalDate.now());
        if (batchTotal == null) {
            batchTotal = BigDecimal.ZERO;
        }

        BigDecimal discrepancy = ingredient.getCurrentStock().subtract(batchTotal);
        boolean consistent = discrepancy.abs().compareTo(new BigDecimal("0.001")) < 0;

        String message = consistent
                ? "Stock is consistent"
                : String.format("Stock discrepancy detected: Ingredient=%s, Batches=%s, Diff=%s",
                        ingredient.getCurrentStock(), batchTotal, discrepancy);

        if (!consistent) {
            log.warn("Stock reconciliation issue for {}: {}", ingredient.getName(), message);
        }

        return new StockReconciliationResult(ingredientId, ingredient.getName(),
                ingredient.getCurrentStock(), batchTotal, discrepancy, consistent, message);
    }

    /**
     * Reconcile all ingredients for a restaurant.
     */
    @Transactional(readOnly = true)
    public List<StockReconciliationResult> reconcileAllStock(Long restaurantId) {
        return ingredientRepository.findByRestaurant_IdAndActiveTrue(restaurantId).stream()
                .map(ingredient -> reconcileStock(ingredient.getId()))
                .filter(result -> !result.consistent())
                .toList();
    }

    /**
     * Execute an operation with retry logic for optimistic locking failures.
     */
    private <T> T executeWithRetry(StockOperation<T> operation, String operationName, Long ingredientId) {
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                return operation.execute();
            } catch (OptimisticLockingFailureException e) {
                if (attempts >= MAX_RETRY_ATTEMPTS) {
                    log.error("Stock operation {} failed for ingredient {} after {} attempts due to concurrent modification",
                            operationName, ingredientId, attempts);
                    throw new StockOperationException(
                            String.format("Concurrent modification detected for ingredient %d. Please retry.",
                                    ingredientId), e);
                }
                log.warn("Optimistic locking failure for {} on ingredient {}, attempt {}/{}. Retrying...",
                        operationName, ingredientId, attempts, MAX_RETRY_ATTEMPTS);
                try {
                    Thread.sleep(RETRY_DELAY_MS * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new StockOperationException("Operation interrupted", ie);
                }
            }
        }
    }

    @FunctionalInterface
    private interface StockOperation<T> {
        T execute();
    }

    // Custom exceptions
    public static class StockOperationException extends RuntimeException {
        public StockOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class InsufficientStockException extends RuntimeException {
        public InsufficientStockException(String message) {
            super(message);
        }
    }

    // ==================== CONVENIENCE METHODS FOR INTERNAL USE ====================

    /**
     * Simple stock addition without starting a new transaction.
     * Use this within an existing transaction when audit logging is not critical.
     * For full audit trail, use addStockWithRetry instead.
     */
    public void addStockSimple(Ingredient ingredient, BigDecimal quantity) {
        ingredient.addStock(quantity);
        log.debug("Stock added (simple): {} {} of {}",
                quantity, ingredient.getUnit(), ingredient.getName());
    }

    /**
     * Simple stock deduction without starting a new transaction.
     * Use this within an existing transaction when audit logging is not critical.
     * For full audit trail, use deductStockWithRetry instead.
     */
    public void deductStockSimple(Ingredient ingredient, BigDecimal quantity) {
        ingredient.deductStock(quantity);
        log.debug("Stock deducted (simple): {} {} of {}",
                quantity, ingredient.getUnit(), ingredient.getName());
    }

    /**
     * Simple force stock deduction without starting a new transaction.
     * Use this within an existing transaction when audit logging is not critical.
     * For full audit trail, use forceDeductStockWithRetry instead.
     */
    public void forceDeductStockSimple(Ingredient ingredient, BigDecimal quantity) {
        ingredient.forceDeductStock(quantity);
        log.debug("Stock force deducted (simple): {} {} of {}",
                quantity, ingredient.getUnit(), ingredient.getName());
    }

    // Result types
    public record StockReconciliationResult(
            Long ingredientId,
            String ingredientName,
            BigDecimal ingredientStock,
            BigDecimal batchTotal,
            BigDecimal discrepancy,
            boolean consistent,
            String message
    ) {}
}
