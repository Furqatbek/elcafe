package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryTransaction;
import com.elcafe.modules.inventory.enums.TransactionType;
import com.elcafe.modules.inventory.repository.InventoryBatchRepository;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.InventoryTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockOperationServiceTest {

    @Mock private InventoryIngredientRepository ingredientRepository;
    @Mock private InventoryBatchRepository batchRepository;
    @Mock private InventoryTransactionRepository transactionRepository;
    @InjectMocks private StockOperationService stockOperationService;

    @Captor private ArgumentCaptor<InventoryTransaction> transactionCaptor;

    private Ingredient ingredient;

    @BeforeEach
    void setUp() {
        ingredient = Ingredient.builder()
                .id(1L)
                .name("Flour")
                .unit("kg")
                .currentStock(new BigDecimal("100.000"))
                .minimumStock(new BigDecimal("10.000"))
                .reorderLevel(new BigDecimal("20.000"))
                .trackInventory(true)
                .trackExpiry(true)
                .active(true)
                .version(0L)
                .build();
    }

    @Nested
    @DisplayName("addStockWithRetry")
    class AddStockWithRetryTests {

        @Test
        @DisplayName("success — adds stock and records transaction")
        void addStock_success() {
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
            when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any(InventoryTransaction.class))).thenAnswer(i -> i.getArgument(0));

            Ingredient result = stockOperationService.addStockWithRetry(1L,
                    new BigDecimal("50"), TransactionType.PURCHASE,
                    "PURCHASE_ORDER", 10L, "Received flour", "admin");

            assertThat(result.getCurrentStock()).isEqualByComparingTo("150.000");
            verify(transactionRepository).save(transactionCaptor.capture());
            InventoryTransaction txn = transactionCaptor.getValue();
            assertThat(txn.getBalanceBefore()).isEqualByComparingTo("100.000");
            assertThat(txn.getBalanceAfter()).isEqualByComparingTo("150.000");
            assertThat(txn.getType()).isEqualTo(TransactionType.PURCHASE);
            assertThat(txn.getPerformedBy()).isEqualTo("admin");
        }

        @Test
        @DisplayName("retries on OptimisticLockingFailureException")
        void addStock_retries() {
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
            when(ingredientRepository.save(any(Ingredient.class)))
                    .thenThrow(new OptimisticLockingFailureException("conflict"))
                    .thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any(InventoryTransaction.class))).thenAnswer(i -> i.getArgument(0));

            Ingredient result = stockOperationService.addStockWithRetry(1L,
                    new BigDecimal("50"), TransactionType.PURCHASE,
                    null, null, null, "admin");

            assertThat(result).isNotNull();
            verify(ingredientRepository, times(2)).save(any(Ingredient.class));
        }

        @Test
        @DisplayName("throws StockOperationException after max retries")
        void addStock_maxRetries_throws() {
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
            when(ingredientRepository.save(any(Ingredient.class)))
                    .thenThrow(new OptimisticLockingFailureException("conflict"));

            assertThatThrownBy(() -> stockOperationService.addStockWithRetry(1L,
                    new BigDecimal("50"), TransactionType.PURCHASE,
                    null, null, null, "admin"))
                    .isInstanceOf(StockOperationService.StockOperationException.class)
                    .hasMessageContaining("Concurrent modification");
        }

        @Test
        @DisplayName("ingredient not found throws")
        void addStock_ingredientNotFound() {
            when(ingredientRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> stockOperationService.addStockWithRetry(99L,
                    new BigDecimal("50"), TransactionType.PURCHASE,
                    null, null, null, "admin"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Ingredient not found");
        }
    }

    @Nested
    @DisplayName("deductStockWithRetry")
    class DeductStockWithRetryTests {

        @Test
        @DisplayName("success — deducts stock and records transaction")
        void deductStock_success() {
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
            when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any(InventoryTransaction.class))).thenAnswer(i -> i.getArgument(0));

            Ingredient result = stockOperationService.deductStockWithRetry(1L,
                    new BigDecimal("30"), TransactionType.ORDER_DEDUCTION,
                    "ORDER", 5L, "Order deduction", "system");

            assertThat(result.getCurrentStock()).isEqualByComparingTo("70.000");
            verify(transactionRepository).save(transactionCaptor.capture());
            InventoryTransaction txn = transactionCaptor.getValue();
            assertThat(txn.getBalanceBefore()).isEqualByComparingTo("100.000");
            assertThat(txn.getBalanceAfter()).isEqualByComparingTo("70.000");
        }

        @Test
        @DisplayName("insufficient stock throws InsufficientStockException")
        void deductStock_insufficientStock_throws() {
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));

            assertThatThrownBy(() -> stockOperationService.deductStockWithRetry(1L,
                    new BigDecimal("200"), TransactionType.ORDER_DEDUCTION,
                    null, null, null, "system"))
                    .isInstanceOf(StockOperationService.InsufficientStockException.class)
                    .hasMessageContaining("Insufficient stock");
        }
    }

    @Nested
    @DisplayName("forceDeductStockWithRetry")
    class ForceDeductTests {

        @Test
        @DisplayName("allows deduction beyond zero — clamps to zero")
        void forceDeduct_clampsToZero() {
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
            when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any(InventoryTransaction.class))).thenAnswer(i -> i.getArgument(0));

            Ingredient result = stockOperationService.forceDeductStockWithRetry(1L,
                    new BigDecimal("200"), TransactionType.WASTE,
                    "WASTE", 1L, "Expired batch write-off", "manager");

            assertThat(result.getCurrentStock()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("reconcileStock")
    class ReconcileStockTests {

        @Test
        @DisplayName("consistent — ingredient matches batch total")
        void reconcile_consistent() {
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
            when(batchRepository.getEffectiveQuantity(eq(1L), any(LocalDate.class)))
                    .thenReturn(new BigDecimal("100.000"));

            StockOperationService.StockReconciliationResult result =
                    stockOperationService.reconcileStock(1L);

            assertThat(result.consistent()).isTrue();
            assertThat(result.discrepancy()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("inconsistent — detects discrepancy")
        void reconcile_inconsistent() {
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
            when(batchRepository.getEffectiveQuantity(eq(1L), any(LocalDate.class)))
                    .thenReturn(new BigDecimal("90.000"));

            StockOperationService.StockReconciliationResult result =
                    stockOperationService.reconcileStock(1L);

            assertThat(result.consistent()).isFalse();
            assertThat(result.discrepancy()).isEqualByComparingTo("10.000");
            assertThat(result.message()).contains("discrepancy");
        }

        @Test
        @DisplayName("skips reconciliation when expiry tracking disabled")
        void reconcile_expiryTrackingDisabled() {
            ingredient.setTrackExpiry(false);
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));

            StockOperationService.StockReconciliationResult result =
                    stockOperationService.reconcileStock(1L);

            assertThat(result.consistent()).isTrue();
            assertThat(result.message()).contains("Expiry tracking disabled");
        }

        @Test
        @DisplayName("handles null batch total")
        void reconcile_nullBatchTotal() {
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
            when(batchRepository.getEffectiveQuantity(eq(1L), any(LocalDate.class))).thenReturn(null);

            StockOperationService.StockReconciliationResult result =
                    stockOperationService.reconcileStock(1L);

            assertThat(result.consistent()).isFalse();
            assertThat(result.batchTotal()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("reconcileAllStock")
    class ReconcileAllStockTests {

        @Test
        @DisplayName("returns only inconsistent results")
        void reconcileAll_filtersConsistent() {
            Ingredient ingredient2 = Ingredient.builder()
                    .id(2L).name("Sugar").unit("kg")
                    .currentStock(new BigDecimal("50.000"))
                    .trackExpiry(true).active(true).version(0L)
                    .build();

            when(ingredientRepository.findByRestaurant_IdAndActiveTrue(1L))
                    .thenReturn(List.of(ingredient, ingredient2));
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
            when(ingredientRepository.findById(2L)).thenReturn(Optional.of(ingredient2));
            // ingredient1 consistent, ingredient2 inconsistent
            when(batchRepository.getEffectiveQuantity(eq(1L), any(LocalDate.class)))
                    .thenReturn(new BigDecimal("100.000"));
            when(batchRepository.getEffectiveQuantity(eq(2L), any(LocalDate.class)))
                    .thenReturn(new BigDecimal("40.000"));

            List<StockOperationService.StockReconciliationResult> results =
                    stockOperationService.reconcileAllStock(1L);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).ingredientName()).isEqualTo("Sugar");
        }
    }

    @Nested
    @DisplayName("Simple operations (in-memory)")
    class SimpleOperationsTests {

        @Test
        @DisplayName("addStockSimple — updates entity in-memory")
        void addStockSimple() {
            stockOperationService.addStockSimple(ingredient, new BigDecimal("25"));
            assertThat(ingredient.getCurrentStock()).isEqualByComparingTo("125.000");
        }

        @Test
        @DisplayName("deductStockSimple — updates entity in-memory")
        void deductStockSimple() {
            stockOperationService.deductStockSimple(ingredient, new BigDecimal("30"));
            assertThat(ingredient.getCurrentStock()).isEqualByComparingTo("70.000");
        }

        @Test
        @DisplayName("forceDeductStockSimple — allows going to zero")
        void forceDeductStockSimple() {
            stockOperationService.forceDeductStockSimple(ingredient, new BigDecimal("200"));
            assertThat(ingredient.getCurrentStock()).isEqualByComparingTo("0");
        }
    }
}
