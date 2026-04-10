package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.dto.AddInputRequest;
import com.elcafe.modules.inventory.dto.CompleteBatchRequest;
import com.elcafe.modules.inventory.dto.CreateProductionBatchRequest;
import com.elcafe.modules.inventory.entity.*;
import com.elcafe.modules.inventory.enums.ValuationMethod;
import com.elcafe.modules.inventory.repository.*;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductionBatchServiceTest {

    @Mock private ProductionBatchRepository batchRepository;
    @Mock private ProductionBatchInputRepository inputRepository;
    @Mock private ProductionBatchConsumptionRepository consumptionRepository;
    @Mock private InventoryValuationService valuationService;
    @Mock private InventoryIngredientRepository ingredientRepository;
    @Mock private InventoryProductIngredientRepository productIngredientRepository;
    @Mock private InventoryTransactionRepository transactionRepository;
    @Mock private ProductRepository productRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @InjectMocks private ProductionBatchService service;

    @Captor private ArgumentCaptor<ProductionBatch> batchCaptor;
    @Captor private ArgumentCaptor<ProductionBatchInput> inputCaptor;
    @Captor private ArgumentCaptor<ProductionBatchConsumption> consumptionCaptor;

    private Restaurant restaurant;
    private Product product;
    private Ingredient meat;
    private Ingredient water;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");

        product = new Product();
        product.setId(10L);
        product.setName("Shurva");
        product.setUsesProductionBatch(true);

        meat = Ingredient.builder()
                .id(1L).name("Meat").unit("kg")
                .currentStock(new BigDecimal("50"))
                .costPerUnit(new BigDecimal("80000"))
                .weightedAverageCost(new BigDecimal("78000"))
                .trackInventory(true).active(true).version(0L).build();
        meat.setRestaurant(restaurant);

        water = Ingredient.builder()
                .id(2L).name("Water").unit("L")
                .currentStock(new BigDecimal("200"))
                .costPerUnit(new BigDecimal("500"))
                .trackInventory(true).active(true).version(0L).build();
        water.setRestaurant(restaurant);

        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(batchRepository.existsByBatchNumber(any())).thenReturn(false);
        when(batchRepository.save(any(ProductionBatch.class))).thenAnswer(i -> {
            ProductionBatch b = i.getArgument(0);
            if (b.getId() == null) b.setId(100L);
            return b;
        });
        when(inputRepository.save(any(ProductionBatchInput.class))).thenAnswer(i -> {
            ProductionBatchInput inp = i.getArgument(0);
            if (inp.getId() == null) inp.setId(200L);
            return inp;
        });
        when(consumptionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private ProductionBatch createDraftBatch() {
        return ProductionBatch.builder()
                .id(100L).restaurant(restaurant).product(product)
                .batchNumber("PB-TEST-001").name("Shurva Morning")
                .outputUnit("L").status(ProductionBatch.Status.DRAFT)
                .outputQuantity(BigDecimal.ZERO).remainingQuantity(BigDecimal.ZERO)
                .totalInputCost(BigDecimal.ZERO).version(0L)
                .inputs(new ArrayList<>()).consumptions(new ArrayList<>())
                .build();
    }

    private ProductionBatch createReadyBatch(BigDecimal remaining) {
        return ProductionBatch.builder()
                .id(100L).restaurant(restaurant).product(product)
                .batchNumber("PB-TEST-001").name("Shurva Morning")
                .outputUnit("L").outputQuantity(new BigDecimal("10"))
                .remainingQuantity(remaining)
                .totalInputCost(new BigDecimal("300000"))
                .costPerUnit(new BigDecimal("30000"))
                .status(ProductionBatch.Status.READY).version(0L)
                .inputs(new ArrayList<>()).consumptions(new ArrayList<>())
                .build();
    }

    // --- createBatch ---

    @Nested @DisplayName("createBatch")
    class CreateBatchTests {

        @Test @DisplayName("creates DRAFT batch without recipe")
        void createDraft() {
            CreateProductionBatchRequest request = CreateProductionBatchRequest.builder()
                    .restaurantId(1L).productId(10L).name("Shurva Morning")
                    .outputUnit("L").preparedBy("Chef Aziz").build();

            ProductionBatch result = service.createBatch(request);

            assertThat(result.getStatus()).isEqualTo(ProductionBatch.Status.DRAFT);
            assertThat(result.getName()).isEqualTo("Shurva Morning");
            assertThat(result.getRestaurant().getId()).isEqualTo(1L);
            assertThat(result.getBatchNumber()).startsWith("PB-");
            verify(batchRepository).save(any(ProductionBatch.class));
        }

        @Test @DisplayName("creates batch without product link")
        void createWithoutProduct() {
            CreateProductionBatchRequest request = CreateProductionBatchRequest.builder()
                    .restaurantId(1L).name("General Soup").outputUnit("L").build();

            ProductionBatch result = service.createBatch(request);

            assertThat(result.getProduct()).isNull();
        }

        @Test @DisplayName("loads recipe inputs when loadRecipe=true")
        void loadRecipe() {
            ProductIngredient piMeat = ProductIngredient.builder()
                    .id(1L).product(product).ingredient(meat)
                    .quantityRequired(new BigDecimal("3")).unit("kg").optional(false).build();
            ProductIngredient piWater = ProductIngredient.builder()
                    .id(2L).product(product).ingredient(water)
                    .quantityRequired(new BigDecimal("8")).unit("L").optional(false).build();
            when(productIngredientRepository.findByProductIdWithIngredients(10L))
                    .thenReturn(List.of(piMeat, piWater));

            CreateProductionBatchRequest request = CreateProductionBatchRequest.builder()
                    .restaurantId(1L).productId(10L).name("Shurva")
                    .outputUnit("L").loadRecipe(true).build();

            service.createBatch(request);

            verify(inputRepository, times(2)).save(inputCaptor.capture());
            List<ProductionBatchInput> savedInputs = inputCaptor.getAllValues();
            assertThat(savedInputs).hasSize(2);
            assertThat(savedInputs.get(0).getPlannedQuantity()).isEqualByComparingTo("3");
            assertThat(savedInputs.get(1).getPlannedQuantity()).isEqualByComparingTo("8");
        }

        @Test @DisplayName("skips optional ingredients when loading recipe")
        void skipsOptionalIngredients() {
            ProductIngredient piMeat = ProductIngredient.builder()
                    .id(1L).product(product).ingredient(meat)
                    .quantityRequired(new BigDecimal("3")).unit("kg").optional(false).build();
            ProductIngredient piOptional = ProductIngredient.builder()
                    .id(3L).product(product).ingredient(water)
                    .quantityRequired(new BigDecimal("1")).unit("L").optional(true).build();
            when(productIngredientRepository.findByProductIdWithIngredients(10L))
                    .thenReturn(List.of(piMeat, piOptional));

            CreateProductionBatchRequest request = CreateProductionBatchRequest.builder()
                    .restaurantId(1L).productId(10L).name("Shurva")
                    .outputUnit("L").loadRecipe(true).build();

            service.createBatch(request);

            verify(inputRepository, times(1)).save(any(ProductionBatchInput.class));
        }
    }

    // --- addInput ---

    @Nested @DisplayName("addInput")
    class AddInputTests {

        @Test @DisplayName("adds input to DRAFT batch")
        void addToDraft() {
            ProductionBatch batch = createDraftBatch();
            when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(meat));

            AddInputRequest request = AddInputRequest.builder()
                    .ingredientId(1L).actualQuantity(new BigDecimal("3")).unit("kg").build();

            ProductionBatchInput result = service.addInput(100L, request);

            assertThat(result.getActualQuantity()).isEqualByComparingTo("3");
            assertThat(result.getIngredient().getName()).isEqualTo("Meat");
        }

        @Test @DisplayName("throws when ingredient not found")
        void ingredientNotFound() {
            ProductionBatch batch = createDraftBatch();
            when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
            when(ingredientRepository.findById(999L)).thenReturn(Optional.empty());

            AddInputRequest request = AddInputRequest.builder()
                    .ingredientId(999L).actualQuantity(new BigDecimal("1")).build();

            assertThatThrownBy(() -> service.addInput(100L, request))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Ingredient not found");
        }

        @Test @DisplayName("throws when batch is READY (not modifiable)")
        void cannotModifyReadyBatch() {
            ProductionBatch batch = createReadyBatch(new BigDecimal("10"));
            when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));

            AddInputRequest request = AddInputRequest.builder()
                    .ingredientId(1L).actualQuantity(new BigDecimal("1")).build();

            assertThatThrownBy(() -> service.addInput(100L, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DRAFT or IN_PROGRESS");
        }
    }

    // --- startBatch ---

    @Nested @DisplayName("startBatch")
    class StartBatchTests {

        @Test @DisplayName("transitions DRAFT to IN_PROGRESS")
        void startDraft() {
            ProductionBatch batch = createDraftBatch();
            when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));

            ProductionBatch result = service.startBatch(100L);

            assertThat(result.getStatus()).isEqualTo(ProductionBatch.Status.IN_PROGRESS);
            assertThat(result.getStartedAt()).isNotNull();
        }

        @Test @DisplayName("throws when already READY")
        void cannotStartReady() {
            ProductionBatch batch = createReadyBatch(new BigDecimal("10"));
            when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));

            assertThatThrownBy(() -> service.startBatch(100L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DRAFT");
        }
    }

    // --- completeBatch ---

    @Nested @DisplayName("completeBatch")
    class CompleteBatchTests {

        @Test @DisplayName("deducts ingredients, calculates cost, sets READY")
        void completeSuccess() {
            ProductionBatch batch = createDraftBatch();
            batch.setStatus(ProductionBatch.Status.IN_PROGRESS);
            when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));

            ProductionBatchInput inputMeat = ProductionBatchInput.builder()
                    .id(1L).productionBatch(batch).ingredient(meat)
                    .actualQuantity(new BigDecimal("3")).unit("kg")
                    .costPerUnit(new BigDecimal("78000"))
                    .totalCost(new BigDecimal("234000")).build();
            ProductionBatchInput inputWater = ProductionBatchInput.builder()
                    .id(2L).productionBatch(batch).ingredient(water)
                    .actualQuantity(new BigDecimal("8")).unit("L")
                    .costPerUnit(new BigDecimal("500"))
                    .totalCost(new BigDecimal("4000")).build();
            when(inputRepository.findByProductionBatchId(100L)).thenReturn(List.of(inputMeat, inputWater));

            when(valuationService.consumeWithValuation(eq(1L), any(), any()))
                    .thenReturn(new InventoryValuationService.ConsumptionResult(
                            new BigDecimal("3"), new BigDecimal("234000"), new BigDecimal("78000"),
                            ValuationMethod.FEFO, List.of()));
            when(valuationService.consumeWithValuation(eq(2L), any(), any()))
                    .thenReturn(new InventoryValuationService.ConsumptionResult(
                            new BigDecimal("8"), new BigDecimal("4000"), new BigDecimal("500"),
                            ValuationMethod.FEFO, List.of()));

            CompleteBatchRequest request = CompleteBatchRequest.builder()
                    .outputQuantity(new BigDecimal("8.5")).outputUnit("L").build();

            ProductionBatch result = service.completeBatch(100L, request);

            assertThat(result.getStatus()).isEqualTo(ProductionBatch.Status.READY);
            assertThat(result.getOutputQuantity()).isEqualByComparingTo("8.5");
            assertThat(result.getRemainingQuantity()).isEqualByComparingTo("8.5");
            assertThat(result.getTotalInputCost()).isEqualByComparingTo("238000");
            assertThat(result.getCostPerUnit()).isNotNull();
            assertThat(result.getCompletedAt()).isNotNull();

            verify(valuationService, times(2)).consumeWithValuation(anyLong(), any(), any());
            verify(transactionRepository, times(2)).save(any());
        }

        @Test @DisplayName("throws when no inputs exist")
        void noInputs() {
            ProductionBatch batch = createDraftBatch();
            when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
            when(inputRepository.findByProductionBatchId(100L)).thenReturn(List.of());

            CompleteBatchRequest request = CompleteBatchRequest.builder()
                    .outputQuantity(new BigDecimal("10")).build();

            assertThatThrownBy(() -> service.completeBatch(100L, request))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no inputs");
        }

        @Test @DisplayName("throws when batch already READY")
        void cannotCompleteReady() {
            ProductionBatch batch = createReadyBatch(new BigDecimal("10"));
            when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));

            CompleteBatchRequest request = CompleteBatchRequest.builder()
                    .outputQuantity(new BigDecimal("10")).build();

            assertThatThrownBy(() -> service.completeBatch(100L, request))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // --- consumeFromBatch ---

    @Nested @DisplayName("consumeFromBatch")
    class ConsumeTests {

        @Test @DisplayName("decrements remaining and creates consumption record")
        void consumeSuccess() {
            ProductionBatch batch = createReadyBatch(new BigDecimal("10"));
            when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));

            BigDecimal cost = service.consumeFromBatch(100L, new BigDecimal("0.5"), 1L, 1L);

            assertThat(cost).isEqualByComparingTo("15000"); // 0.5 * 30000
            assertThat(batch.getRemainingQuantity()).isEqualByComparingTo("9.5");
            assertThat(batch.getStatus()).isEqualTo(ProductionBatch.Status.SERVING);
            verify(consumptionRepository).save(consumptionCaptor.capture());
            assertThat(consumptionCaptor.getValue().getQuantity()).isEqualByComparingTo("0.5");
        }

        @Test @DisplayName("depletes batch when remaining reaches zero")
        void consumeDepletes() {
            ProductionBatch batch = createReadyBatch(new BigDecimal("0.5"));
            when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));

            service.consumeFromBatch(100L, new BigDecimal("0.5"), 1L, 1L);

            assertThat(batch.getRemainingQuantity()).isEqualByComparingTo("0");
            assertThat(batch.getStatus()).isEqualTo(ProductionBatch.Status.DEPLETED);
        }

        @Test @DisplayName("throws when insufficient remaining")
        void insufficientRemaining() {
            ProductionBatch batch = createReadyBatch(new BigDecimal("0.3"));
            when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));

            assertThatThrownBy(() -> service.consumeFromBatch(100L, new BigDecimal("0.5"), 1L, 1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Insufficient remaining quantity");
        }

        @Test @DisplayName("throws when batch is not available")
        void notAvailable() {
            ProductionBatch batch = createDraftBatch();
            when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));

            assertThatThrownBy(() -> service.consumeFromBatch(100L, new BigDecimal("1"), 1L, 1L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not available");
        }
    }

    // --- findAvailableBatch ---

    @Nested @DisplayName("findAvailableBatch")
    class FindAvailableTests {

        @Test @DisplayName("returns first FEFO batch")
        void findsAvailable() {
            ProductionBatch batch = createReadyBatch(new BigDecimal("5"));
            when(batchRepository.findAvailableByProduct(10L)).thenReturn(List.of(batch));

            ProductionBatch result = service.findAvailableBatch(10L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(100L);
        }

        @Test @DisplayName("returns null when none available")
        void noneAvailable() {
            when(batchRepository.findAvailableByProduct(10L)).thenReturn(List.of());

            ProductionBatch result = service.findAvailableBatch(10L);

            assertThat(result).isNull();
        }
    }

    // --- consumeForOrder (variant-based deduction) ---

    @Nested @DisplayName("consumeForOrder")
    class ConsumeForOrderTests {

        @BeforeEach
        void setUpBatch() {
            ProductionBatch batch = createReadyBatch(new BigDecimal("100"));
            when(batchRepository.findAvailableByProduct(10L)).thenReturn(List.of(batch));
            when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));
        }

        @Test @DisplayName("variant batchDeductionQty=5, quantity=2 → deducts 10")
        void variantMultiplier() {
            BigDecimal cost = service.consumeForOrder(10L, 2, null, new BigDecimal("5"), 1L, 1L);

            // 10 units × 30000 cost/unit = 300000
            assertThat(cost).isEqualByComparingTo("300000");
            verify(consumptionRepository).save(consumptionCaptor.capture());
            assertThat(consumptionCaptor.getValue().getQuantity()).isEqualByComparingTo("10");
        }

        @Test @DisplayName("variant batchDeductionQty=1, quantity=3 → deducts 3")
        void variantSinglePiece() {
            BigDecimal cost = service.consumeForOrder(10L, 3, null, new BigDecimal("1"), 1L, 1L);

            assertThat(cost).isEqualByComparingTo("90000"); // 3 × 30000
            verify(consumptionRepository).save(consumptionCaptor.capture());
            assertThat(consumptionCaptor.getValue().getQuantity()).isEqualByComparingTo("3");
        }

        @Test @DisplayName("weightAmount takes priority over batchDeductionQty")
        void weightOverridesVariant() {
            BigDecimal cost = service.consumeForOrder(10L, 1, new BigDecimal("0.5"),
                    new BigDecimal("5"), 1L, 1L);

            // Weight wins: 0.5 × 30000 = 15000
            assertThat(cost).isEqualByComparingTo("15000");
            verify(consumptionRepository).save(consumptionCaptor.capture());
            assertThat(consumptionCaptor.getValue().getQuantity()).isEqualByComparingTo("0.5");
        }

        @Test @DisplayName("null batchDeductionQty falls back to itemQuantity")
        void fallbackToQuantity() {
            BigDecimal cost = service.consumeForOrder(10L, 2, null, null, 1L, 1L);

            assertThat(cost).isEqualByComparingTo("60000"); // 2 × 30000
            verify(consumptionRepository).save(consumptionCaptor.capture());
            assertThat(consumptionCaptor.getValue().getQuantity()).isEqualByComparingTo("2");
        }

        @Test @DisplayName("zero batchDeductionQty falls back to itemQuantity")
        void zeroBatchDeductionFallback() {
            BigDecimal cost = service.consumeForOrder(10L, 3, null, BigDecimal.ZERO, 1L, 1L);

            assertThat(cost).isEqualByComparingTo("90000"); // 3 × 30000
        }

        @Test @DisplayName("throws when no batch available")
        void noBatchAvailable() {
            when(batchRepository.findAvailableByProduct(999L)).thenReturn(List.of());

            assertThatThrownBy(() -> service.consumeForOrder(999L, 1, null, null, 1L, 1L))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("No available production batch");
        }
    }

    // --- recordWaste ---

    @Nested @DisplayName("recordWaste")
    class WasteTests {

        @Test @DisplayName("decrements remaining and sets WASTED when fully wasted")
        void wasteAll() {
            ProductionBatch batch = createReadyBatch(new BigDecimal("2"));
            when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));

            service.recordWaste(100L, new BigDecimal("2"), "End of day leftover");

            assertThat(batch.getRemainingQuantity()).isEqualByComparingTo("0");
            assertThat(batch.getStatus()).isEqualTo(ProductionBatch.Status.WASTED);
            verify(batchRepository).save(batch);
        }

        @Test @DisplayName("partially wastes batch without changing status")
        void wastePartial() {
            ProductionBatch batch = createReadyBatch(new BigDecimal("5"));
            when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));

            service.recordWaste(100L, new BigDecimal("2"), "Spillage");

            assertThat(batch.getRemainingQuantity()).isEqualByComparingTo("3");
            assertThat(batch.getStatus()).isEqualTo(ProductionBatch.Status.READY);
        }

        @Test @DisplayName("caps waste at remaining quantity")
        void capsAtRemaining() {
            ProductionBatch batch = createReadyBatch(new BigDecimal("1"));
            when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));

            service.recordWaste(100L, new BigDecimal("5"), "Overestimated");

            assertThat(batch.getRemainingQuantity()).isEqualByComparingTo("0");
        }
    }

    // --- deleteBatch ---

    @Nested @DisplayName("deleteBatch")
    class DeleteTests {

        @Test @DisplayName("deletes DRAFT batch")
        void deleteDraft() {
            ProductionBatch batch = createDraftBatch();
            when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));

            service.deleteBatch(100L);

            verify(batchRepository).delete(batch);
        }

        @Test @DisplayName("throws when deleting non-DRAFT batch")
        void cannotDeleteReady() {
            ProductionBatch batch = createReadyBatch(new BigDecimal("10"));
            when(batchRepository.findById(100L)).thenReturn(Optional.of(batch));

            assertThatThrownBy(() -> service.deleteBatch(100L))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("DRAFT");
        }
    }

    // --- getActiveBatches / getBatchesByRestaurant ---

    @Nested @DisplayName("query methods")
    class QueryTests {

        @Test @DisplayName("getActiveBatches delegates to repository")
        void activeBatches() {
            when(batchRepository.findActiveBatches(1L)).thenReturn(List.of(createReadyBatch(new BigDecimal("5"))));
            assertThat(service.getActiveBatches(1L)).hasSize(1);
        }

        @Test @DisplayName("getBatchesByRestaurant delegates to repository")
        void allBatches() {
            when(batchRepository.findByRestaurantIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
            assertThat(service.getBatchesByRestaurant(1L)).isEmpty();
        }
    }
}
