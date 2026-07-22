package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.IngredientCostHistory;
import com.elcafe.modules.inventory.entity.InventoryBatch;
import com.elcafe.modules.inventory.enums.CostChangeReason;
import com.elcafe.modules.inventory.repository.IngredientCostHistoryRepository;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CostHistoryServiceTest {

    @Mock private IngredientCostHistoryRepository costHistoryRepository;
    @Mock private InventoryIngredientRepository ingredientRepository;
    @InjectMocks private CostHistoryService costHistoryService;

    @Captor private ArgumentCaptor<IngredientCostHistory> historyCaptor;

    private Ingredient ingredient;

    @BeforeEach
    void setUp() {
        ingredient = Ingredient.builder()
                .id(1L)
                .name("Flour")
                .unit("kg")
                .currentStock(new BigDecimal("100"))
                .costPerUnit(new BigDecimal("5000"))
                .weightedAverageCost(new BigDecimal("4800"))
                .active(true)
                .version(0L)
                .build();
    }

    @Nested
    @DisplayName("recordCostChange")
    class RecordCostChangeTests {

        @Test
        @DisplayName("records cost change with previous cost")
        void recordCostChange_success() {
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
            when(costHistoryRepository.findFirstByIngredientIdOrderByEffectiveFromDesc(1L))
                    .thenReturn(Optional.empty());
            when(costHistoryRepository.save(any(IngredientCostHistory.class)))
                    .thenAnswer(i -> { IngredientCostHistory h = i.getArgument(0); h.setId(1L); return h; });
            when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(i -> i.getArgument(0));

            IngredientCostHistory result = costHistoryService.recordCostChange(
                    1L, new BigDecimal("6000"), CostChangeReason.MANUAL_ADJUSTMENT, "admin");

            assertThat(result).isNotNull();
            verify(costHistoryRepository).save(historyCaptor.capture());
            IngredientCostHistory saved = historyCaptor.getValue();
            assertThat(saved.getPreviousCost()).isEqualByComparingTo("5000");
            assertThat(saved.getNewCost()).isEqualByComparingTo("6000");
            assertThat(saved.getReason()).isEqualTo(CostChangeReason.MANUAL_ADJUSTMENT);
            assertThat(saved.getCreatedBy()).isEqualTo("admin");

            // Verify ingredient cost is updated
            verify(ingredientRepository).save(any(Ingredient.class));
            assertThat(ingredient.getCostPerUnit()).isEqualByComparingTo("6000");
        }

        @Test
        @DisplayName("closes previous history record")
        void recordCostChange_closesPrevious() {
            IngredientCostHistory previousHistory = IngredientCostHistory.builder()
                    .id(1L)
                    .ingredient(ingredient)
                    .previousCost(new BigDecimal("4000"))
                    .newCost(new BigDecimal("5000"))
                    .reason(CostChangeReason.PURCHASE)
                    .effectiveFrom(LocalDateTime.now().minusDays(30))
                    .build();

            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
            when(costHistoryRepository.findFirstByIngredientIdOrderByEffectiveFromDesc(1L))
                    .thenReturn(Optional.of(previousHistory));
            when(costHistoryRepository.save(any(IngredientCostHistory.class)))
                    .thenAnswer(i -> { IngredientCostHistory h = i.getArgument(0); h.setId(2L); return h; });
            when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(i -> i.getArgument(0));

            costHistoryService.recordCostChange(1L, new BigDecimal("5500"),
                    CostChangeReason.SUPPLIER_UPDATE, "admin");

            // Previous record should have effectiveTo set
            assertThat(previousHistory.getEffectiveTo()).isNotNull();
        }

        @Test
        @DisplayName("ingredient not found throws")
        void recordCostChange_ingredientNotFound_throws() {
            when(ingredientRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> costHistoryService.recordCostChange(
                    99L, new BigDecimal("6000"), CostChangeReason.MANUAL_ADJUSTMENT, "admin"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Ingredient not found");
        }

        @Test
        @DisplayName("handles null costPerUnit — defaults to zero")
        void recordCostChange_nullCostPerUnit() {
            ingredient.setCostPerUnit(null);
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
            when(costHistoryRepository.findFirstByIngredientIdOrderByEffectiveFromDesc(1L))
                    .thenReturn(Optional.empty());
            when(costHistoryRepository.save(any(IngredientCostHistory.class)))
                    .thenAnswer(i -> { IngredientCostHistory h = i.getArgument(0); h.setId(1L); return h; });
            when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(i -> i.getArgument(0));

            costHistoryService.recordCostChange(1L, new BigDecimal("5000"),
                    CostChangeReason.INITIAL_SETUP, "admin");

            verify(costHistoryRepository).save(historyCaptor.capture());
            assertThat(historyCaptor.getValue().getPreviousCost()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("recordCostChangeFromPurchase")
    class RecordCostChangeFromPurchaseTests {

        @Test
        @DisplayName("records with batch and purchase order references")
        void recordFromPurchase_withBatchAndPO() {
            InventoryBatch batch = InventoryBatch.builder()
                    .id(10L)
                    .batchNumber("BATCH-001")
                    .build();

            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
            when(costHistoryRepository.findFirstByIngredientIdOrderByEffectiveFromDesc(1L))
                    .thenReturn(Optional.empty());
            when(costHistoryRepository.save(any(IngredientCostHistory.class)))
                    .thenAnswer(i -> { IngredientCostHistory h = i.getArgument(0); h.setId(1L); return h; });
            when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(i -> i.getArgument(0));

            IngredientCostHistory result = costHistoryService.recordCostChangeFromPurchase(
                    1L, new BigDecimal("5500"), batch, 100L, "admin");

            verify(costHistoryRepository).save(historyCaptor.capture());
            IngredientCostHistory saved = historyCaptor.getValue();
            assertThat(saved.getReason()).isEqualTo(CostChangeReason.PURCHASE);
            assertThat(saved.getBatch()).isEqualTo(batch);
            assertThat(saved.getPurchaseOrderId()).isEqualTo(100L);
        }
    }

    @Nested
    @DisplayName("Query methods")
    class QueryTests {

        @Test
        @DisplayName("getCostHistory — delegates to repository")
        void getCostHistory() {
            IngredientCostHistory history = IngredientCostHistory.builder()
                    .id(1L).ingredient(ingredient)
                    .previousCost(new BigDecimal("4000")).newCost(new BigDecimal("5000"))
                    .reason(CostChangeReason.PURCHASE)
                    .build();
            when(costHistoryRepository.findByIngredientIdOrderByEffectiveFromDesc(1L))
                    .thenReturn(List.of(history));

            List<IngredientCostHistory> result = costHistoryService.getCostHistory(1L);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("getCostHistoryPaginated — returns page")
        void getCostHistoryPaginated() {
            IngredientCostHistory history = IngredientCostHistory.builder().id(1L).build();
            when(costHistoryRepository.findByIngredientId(eq(1L), any(PageRequest.class)))
                    .thenReturn(new PageImpl<>(List.of(history)));

            Page<IngredientCostHistory> result = costHistoryService.getCostHistoryPaginated(1L, 0, 10);

            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("getCostAtDateTime — returns cost from history")
        void getCostAtDateTime_fromHistory() {
            IngredientCostHistory history = IngredientCostHistory.builder()
                    .id(1L).newCost(new BigDecimal("4500")).build();
            when(costHistoryRepository.findCostAtDateTime(eq(1L), any(LocalDateTime.class)))
                    .thenReturn(List.of(history));

            Optional<BigDecimal> result = costHistoryService.getCostAtDateTime(1L, LocalDateTime.now().minusDays(10));

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualByComparingTo("4500");
        }

        @Test
        @DisplayName("getCostAtDateTime — falls back to current cost when no history")
        void getCostAtDateTime_fallback() {
            when(costHistoryRepository.findCostAtDateTime(eq(1L), any(LocalDateTime.class)))
                    .thenReturn(List.of());
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));

            Optional<BigDecimal> result = costHistoryService.getCostAtDateTime(1L, LocalDateTime.now().minusDays(10));

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualByComparingTo("5000");
        }

        @Test
        @DisplayName("getCostChangesInRange — delegates")
        void getCostChangesInRange() {
            LocalDateTime start = LocalDateTime.now().minusDays(30);
            LocalDateTime end = LocalDateTime.now();
            when(costHistoryRepository.findByIngredientIdAndDateRange(1L, start, end))
                    .thenReturn(List.of());

            List<IngredientCostHistory> result = costHistoryService.getCostChangesInRange(1L, start, end);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("getRestaurantCostChanges — delegates")
        void getRestaurantCostChanges() {
            LocalDateTime start = LocalDateTime.now().minusDays(30);
            LocalDateTime end = LocalDateTime.now();
            when(costHistoryRepository.findByRestaurant_IdAndDateRange(1L, start, end))
                    .thenReturn(List.of());

            List<IngredientCostHistory> result = costHistoryService.getRestaurantCostChanges(1L, start, end);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("getCostChangesByReason — filters by reason")
        void getCostChangesByReason() {
            when(costHistoryRepository.findByIngredientIdAndReasonOrderByEffectiveFromDesc(
                    1L, CostChangeReason.PURCHASE)).thenReturn(List.of());

            List<IngredientCostHistory> result = costHistoryService.getCostChangesByReason(
                    1L, CostChangeReason.PURCHASE);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("getAverageCostInPeriod — delegates to repository")
        void getAverageCostInPeriod() {
            LocalDateTime start = LocalDateTime.now().minusDays(30);
            LocalDateTime end = LocalDateTime.now();
            when(costHistoryRepository.getAverageCostInPeriod(1L, start, end))
                    .thenReturn(Optional.of(new BigDecimal("4500")));

            Optional<BigDecimal> result = costHistoryService.getAverageCostInPeriod(1L, start, end);

            assertThat(result).isPresent();
            assertThat(result.get()).isEqualByComparingTo("4500");
        }

        @Test
        @DisplayName("getAverageCostInPeriod — returns empty when no data")
        void getAverageCostInPeriod_empty() {
            LocalDateTime start = LocalDateTime.now().minusDays(30);
            LocalDateTime end = LocalDateTime.now();
            when(costHistoryRepository.getAverageCostInPeriod(1L, start, end))
                    .thenReturn(Optional.empty());

            Optional<BigDecimal> result = costHistoryService.getAverageCostInPeriod(1L, start, end);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("getMostRecentCostChange — returns latest")
        void getMostRecentCostChange() {
            IngredientCostHistory history = IngredientCostHistory.builder().id(1L).build();
            when(costHistoryRepository.findFirstByIngredientIdOrderByEffectiveFromDesc(1L))
                    .thenReturn(Optional.of(history));

            Optional<IngredientCostHistory> result = costHistoryService.getMostRecentCostChange(1L);

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("countCostChanges — delegates")
        void countCostChanges() {
            when(costHistoryRepository.countByIngredientId(1L)).thenReturn(5L);

            long count = costHistoryService.countCostChanges(1L);

            assertThat(count).isEqualTo(5L);
        }
    }

    @Nested
    @DisplayName("calculateCostVariance")
    class CostVarianceTests {

        @Test
        @DisplayName("calculates variance between current and average cost")
        void calculateCostVariance_success() {
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
            LocalDateTime start = LocalDateTime.now().minusDays(30);
            LocalDateTime end = LocalDateTime.now();
            when(costHistoryRepository.getAverageCostInPeriod(1L, start, end))
                    .thenReturn(Optional.of(new BigDecimal("4000")));

            CostHistoryService.CostVariance result = costHistoryService.calculateCostVariance(1L, start, end);

            assertThat(result.currentCost()).isEqualByComparingTo("5000");
            assertThat(result.averageCost()).isEqualByComparingTo("4000");
            assertThat(result.variance()).isEqualByComparingTo("1000");
            assertThat(result.variancePercent()).isEqualByComparingTo("25.0000");
        }

        @Test
        @DisplayName("returns zero variance when no average available")
        void calculateCostVariance_noAverage() {
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
            LocalDateTime start = LocalDateTime.now().minusDays(30);
            LocalDateTime end = LocalDateTime.now();
            when(costHistoryRepository.getAverageCostInPeriod(1L, start, end))
                    .thenReturn(Optional.empty());

            CostHistoryService.CostVariance result = costHistoryService.calculateCostVariance(1L, start, end);

            assertThat(result.variance()).isEqualByComparingTo("0");
            assertThat(result.variancePercent()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("handles null costPerUnit on ingredient")
        void calculateCostVariance_nullCostPerUnit() {
            ingredient.setCostPerUnit(null);
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
            LocalDateTime start = LocalDateTime.now().minusDays(30);
            LocalDateTime end = LocalDateTime.now();
            when(costHistoryRepository.getAverageCostInPeriod(1L, start, end))
                    .thenReturn(Optional.of(new BigDecimal("4000")));

            CostHistoryService.CostVariance result = costHistoryService.calculateCostVariance(1L, start, end);

            assertThat(result.currentCost()).isEqualByComparingTo("0");
        }
    }
}
