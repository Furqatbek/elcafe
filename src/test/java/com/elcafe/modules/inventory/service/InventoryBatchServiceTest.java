package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.dto.BatchRequest;
import com.elcafe.modules.inventory.dto.BatchResponse;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryBatch;
import com.elcafe.modules.inventory.entity.Supplier;
import com.elcafe.modules.inventory.exception.BatchNotFoundException;
import com.elcafe.modules.inventory.exception.DuplicateBatchNumberException;
import com.elcafe.modules.inventory.exception.IngredientNotFoundException;
import com.elcafe.modules.inventory.repository.InventoryBatchRepository;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.SupplierRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryBatchServiceTest {

    @Mock private InventoryBatchRepository batchRepository;
    @Mock private InventoryIngredientRepository ingredientRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private WasteService wasteService;
    @Mock private StockOperationService stockOperationService;
    @InjectMocks private InventoryBatchService batchService;

    private Ingredient ingredient;
    private InventoryBatch batch;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);

        ingredient = Ingredient.builder()
                .id(1L).name("Flour").unit("kg")
                .currentStock(new BigDecimal("100"))
                .costPerUnit(new BigDecimal("5000"))
                .expiryAlertDays(7).defaultShelfLifeDays(30)
                .trackExpiry(true).active(true).version(0L).build();
        ingredient.setRestaurant(restaurant);

        batch = InventoryBatch.builder()
                .id(1L).batchNumber("BATCH-001")
                .ingredient(ingredient)
                .quantity(new BigDecimal("50")).initialQuantity(new BigDecimal("50"))
                .receivedDate(LocalDate.now()).expiryDate(LocalDate.now().plusDays(30))
                .costPerUnit(new BigDecimal("5000"))
                .status(InventoryBatch.Status.ACTIVE).build();
    }

    @Nested @DisplayName("createBatch")
    class CreateBatchTests {
        @Test @DisplayName("with provided batch number")
        void withBatchNumber() {
            BatchRequest request = BatchRequest.builder()
                    .ingredientId(1L).batchNumber("MY-BATCH-001")
                    .quantity(new BigDecimal("50")).receivedDate(LocalDate.now())
                    .expiryDate(LocalDate.now().plusDays(30)).build();
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
            when(batchRepository.findByIngredientIdAndBatchNumber(1L, "MY-BATCH-001")).thenReturn(Optional.empty());
            when(batchRepository.save(any(InventoryBatch.class))).thenAnswer(i -> { InventoryBatch b = i.getArgument(0); b.setId(1L); return b; });
            when(ingredientRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            InventoryBatch result = batchService.createBatch(request);

            assertThat(result.getBatchNumber()).isEqualTo("MY-BATCH-001");
            verify(stockOperationService).addStockSimple(ingredient, new BigDecimal("50"));
        }

        @Test @DisplayName("auto-generates batch number when not provided")
        void generatesNumber() {
            BatchRequest request = BatchRequest.builder()
                    .ingredientId(1L).quantity(new BigDecimal("25")).receivedDate(LocalDate.now()).build();
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
            when(batchRepository.findByIngredientIdAndBatchNumber(anyLong(), anyString())).thenReturn(Optional.empty());
            when(batchRepository.save(any(InventoryBatch.class))).thenAnswer(i -> { InventoryBatch b = i.getArgument(0); b.setId(2L); return b; });
            when(ingredientRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            InventoryBatch result = batchService.createBatch(request);

            assertThat(result.getBatchNumber()).startsWith("B-1-");
        }

        @Test @DisplayName("throws on duplicate batch number")
        void duplicateNumber() {
            BatchRequest request = BatchRequest.builder()
                    .ingredientId(1L).batchNumber("BATCH-001")
                    .quantity(new BigDecimal("50")).receivedDate(LocalDate.now()).build();
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
            when(batchRepository.findByIngredientIdAndBatchNumber(1L, "BATCH-001")).thenReturn(Optional.of(batch));

            assertThatThrownBy(() -> batchService.createBatch(request))
                    .isInstanceOf(DuplicateBatchNumberException.class);
        }

        @Test @DisplayName("calculates expiry from shelf life days")
        void calculatesExpiry() {
            BatchRequest request = BatchRequest.builder()
                    .ingredientId(1L).quantity(new BigDecimal("25")).receivedDate(LocalDate.now()).build();
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
            when(batchRepository.findByIngredientIdAndBatchNumber(anyLong(), anyString())).thenReturn(Optional.empty());
            when(batchRepository.save(any(InventoryBatch.class))).thenAnswer(i -> i.getArgument(0));
            when(ingredientRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            InventoryBatch result = batchService.createBatch(request);

            assertThat(result.getExpiryDate()).isEqualTo(LocalDate.now().plusDays(30));
        }

        @Test @DisplayName("uses provided expiry date over calculated")
        void usesProvidedExpiry() {
            LocalDate customExpiry = LocalDate.now().plusDays(90);
            BatchRequest request = BatchRequest.builder()
                    .ingredientId(1L).quantity(new BigDecimal("25"))
                    .receivedDate(LocalDate.now()).expiryDate(customExpiry).build();
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
            when(batchRepository.findByIngredientIdAndBatchNumber(anyLong(), anyString())).thenReturn(Optional.empty());
            when(batchRepository.save(any(InventoryBatch.class))).thenAnswer(i -> i.getArgument(0));
            when(ingredientRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            InventoryBatch result = batchService.createBatch(request);

            assertThat(result.getExpiryDate()).isEqualTo(customExpiry);
        }
    }

    @Nested @DisplayName("Query methods")
    class QueryTests {
        @Test @DisplayName("getBatchesByIngredient — returns mapped list")
        void getBatches() {
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
            when(batchRepository.findByIngredientIdOrderByExpiryDateAsc(1L)).thenReturn(List.of(batch));
            List<BatchResponse> result = batchService.getBatchesByIngredient(1L);
            assertThat(result).hasSize(1);
        }

        @Test @DisplayName("getExpiringBatches — returns expiring")
        void getExpiring() {
            when(batchRepository.findExpiringBatches(eq(1L), any(LocalDate.class))).thenReturn(List.of(batch));
            assertThat(batchService.getExpiringBatches(1L, 7)).hasSize(1);
        }

        @Test @DisplayName("getExpiredBatches — returns expired")
        void getExpired() {
            when(batchRepository.findExpiredBatches(eq(1L), any(LocalDate.class))).thenReturn(List.of());
            assertThat(batchService.getExpiredBatches(1L)).isEmpty();
        }

        @Test @DisplayName("getExpirySummary — returns counts")
        void getSummary() {
            when(batchRepository.countExpiredBatches(eq(1L), any(LocalDate.class))).thenReturn(2L);
            when(batchRepository.countExpiringBatches(eq(1L), any(LocalDate.class), any(LocalDate.class))).thenReturn(3L);
            InventoryBatchService.ExpirySummary summary = batchService.getExpirySummary(1L, 7);
            assertThat(summary.expiredCount()).isEqualTo(2);
            assertThat(summary.expiringCount()).isEqualTo(3);
        }

        @Test @DisplayName("getEffectiveStock — delegates")
        void effectiveStock() {
            when(batchRepository.getEffectiveQuantity(eq(1L), any(LocalDate.class))).thenReturn(new BigDecimal("95"));
            assertThat(batchService.getEffectiveStock(1L)).isEqualByComparingTo("95");
        }
    }

    @Nested @DisplayName("writeOffBatch")
    class WriteOffTests {
        @Test @DisplayName("writes off batch and deducts stock")
        void writeOff() {
            when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
            when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(ingredientRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            batchService.writeOffBatch(1L, "Damaged", "admin");

            assertThat(batch.getStatus()).isEqualTo(InventoryBatch.Status.WRITTEN_OFF);
            verify(stockOperationService).deductStockSimple(ingredient, new BigDecimal("50"));
            verify(wasteService).recordWasteFromBatch(any(), any(), eq("admin"), eq("Damaged"));
        }

        @Test @DisplayName("not found throws")
        void notFound() {
            when(batchRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> batchService.writeOffBatch(99L, "test"))
                    .isInstanceOf(BatchNotFoundException.class);
        }
    }

    @Test @DisplayName("updateBatchExpiry — updates date and reactivates if needed")
    void updateExpiry() {
        batch.setStatus(InventoryBatch.Status.EXPIRED);
        batch.setExpiryDate(LocalDate.now().minusDays(1));
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        InventoryBatch result = batchService.updateBatchExpiry(1L, LocalDate.now().plusDays(30));

        assertThat(result.getExpiryDate()).isEqualTo(LocalDate.now().plusDays(30));
        assertThat(result.getStatus()).isEqualTo(InventoryBatch.Status.ACTIVE);
    }

    @Test @DisplayName("markExpiredBatches — marks and deducts stock")
    void markExpired() {
        InventoryBatch expiredBatch = InventoryBatch.builder()
                .id(2L).batchNumber("EXP-001").ingredient(ingredient)
                .quantity(new BigDecimal("10")).status(InventoryBatch.Status.ACTIVE)
                .expiryDate(LocalDate.now().minusDays(1)).build();
        when(batchRepository.findExpiredBatches(eq(1L), any(LocalDate.class))).thenReturn(List.of(expiredBatch));
        when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(ingredientRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        int count = batchService.markExpiredBatches(1L);

        assertThat(count).isEqualTo(1);
        verify(stockOperationService).forceDeductStockSimple(ingredient, new BigDecimal("10"));
    }
}
