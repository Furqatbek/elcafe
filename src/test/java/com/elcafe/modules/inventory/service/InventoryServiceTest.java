package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryTransaction;
import com.elcafe.modules.inventory.entity.ProductIngredient;
import com.elcafe.modules.inventory.enums.ValuationMethod;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.inventory.repository.InventoryTransactionRepository;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.ownerbot.service.OwnerNotificationService;
import com.elcafe.modules.restaurant.entity.Restaurant;
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
class InventoryServiceTest {

    @Mock private InventoryIngredientRepository ingredientRepository;
    @Mock private InventoryProductIngredientRepository productIngredientRepository;
    @Mock private InventoryTransactionRepository transactionRepository;
    @Mock private InventoryValuationService valuationService;
    @Mock private OwnerNotificationService ownerNotificationService;
    @InjectMocks private InventoryService inventoryService;

    @Captor private ArgumentCaptor<InventoryTransaction> txnCaptor;

    private Restaurant restaurant;
    private Ingredient flour;
    private ProductIngredient recipeFlour;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test");

        flour = Ingredient.builder()
                .id(1L).name("Flour").unit("kg")
                .currentStock(new BigDecimal("100"))
                .minimumStock(new BigDecimal("10"))
                .reorderLevel(new BigDecimal("20"))
                .costPerUnit(new BigDecimal("5000"))
                .weightedAverageCost(new BigDecimal("4800"))
                .trackInventory(true).active(true).version(0L).build();
        flour.setRestaurant(restaurant);

        Product product = new Product();
        product.setId(1L);
        product.setName("Bread");

        recipeFlour = ProductIngredient.builder()
                .id(1L).product(product).ingredient(flour)
                .quantityRequired(new BigDecimal("0.5")).optional(false).build();
    }

    private Order createTestOrder(int qty) {
        Order order = new Order();
        order.setId(1L);
        order.setOrderNumber("ORD-001");
        OrderItem item = OrderItem.builder().productId(1L).quantity(qty).build();
        order.setItems(new ArrayList<>(List.of(item)));
        return order;
    }

    @Nested @DisplayName("checkIngredientAvailability")
    class CheckAvailabilityTests {
        @Test @DisplayName("all available — returns true")
        void allAvailable() {
            when(productIngredientRepository.findByProductIdWithIngredients(1L)).thenReturn(List.of(recipeFlour));
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(flour));
            assertThat(inventoryService.checkIngredientAvailability(createTestOrder(2))).isTrue();
        }

        @Test @DisplayName("insufficient stock — returns false")
        void insufficient() {
            flour.setCurrentStock(new BigDecimal("0.1"));
            when(productIngredientRepository.findByProductIdWithIngredients(1L)).thenReturn(List.of(recipeFlour));
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(flour));
            assertThat(inventoryService.checkIngredientAvailability(createTestOrder(2))).isFalse();
        }
    }

    @Nested @DisplayName("deductIngredientsForOrder")
    class DeductTests {
        @Test @DisplayName("success — uses valuation service")
        void success() {
            when(productIngredientRepository.findByProductIdWithIngredients(1L)).thenReturn(List.of(recipeFlour));
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(flour));
            when(valuationService.consumeWithValuation(eq(1L), any(BigDecimal.class), eq(1L)))
                    .thenReturn(new InventoryValuationService.ConsumptionResult(
                            new BigDecimal("1"), new BigDecimal("5000"), new BigDecimal("5000"),
                            ValuationMethod.WEIGHTED_AVERAGE, List.of()));
            when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            inventoryService.deductIngredientsForOrder(createTestOrder(2));

            verify(transactionRepository).save(txnCaptor.capture());
            assertThat(txnCaptor.getValue().getCostPerUnit()).isEqualByComparingTo("5000");
        }

        @Test @DisplayName("fallback — degrades when valuation fails")
        void fallback() {
            when(productIngredientRepository.findByProductIdWithIngredients(1L)).thenReturn(List.of(recipeFlour));
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(flour));
            when(valuationService.consumeWithValuation(anyLong(), any(), anyLong()))
                    .thenThrow(new RuntimeException("No batches"));
            when(ingredientRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            inventoryService.deductIngredientsForOrder(createTestOrder(2));

            verify(transactionRepository).save(txnCaptor.capture());
            assertThat(txnCaptor.getValue().getNotes()).contains("VALUATION_FAILED");
        }

        @Test @DisplayName("sends low stock alert when below minimum")
        void lowStockAlert() {
            flour.setCurrentStock(new BigDecimal("10"));
            flour.setMinimumStock(new BigDecimal("10"));
            when(productIngredientRepository.findByProductIdWithIngredients(1L)).thenReturn(List.of(recipeFlour));
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(flour));
            when(valuationService.consumeWithValuation(anyLong(), any(), anyLong()))
                    .thenReturn(new InventoryValuationService.ConsumptionResult(
                            new BigDecimal("1"), new BigDecimal("5000"), new BigDecimal("5000"),
                            ValuationMethod.WEIGHTED_AVERAGE, List.of()));
            when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            inventoryService.deductIngredientsForOrder(createTestOrder(2));

            verify(ownerNotificationService).notifyLowStock(eq(1L), eq("Flour"), anyInt(), anyInt());
        }
    }

    @Nested @DisplayName("addStock")
    class AddStockTests {
        @Test @DisplayName("without cost — uses effective cost")
        void withoutCost() {
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(flour));
            when(ingredientRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            inventoryService.addStock(1L, new BigDecimal("50"), "Delivery", "admin");

            assertThat(flour.getCurrentStock()).isEqualByComparingTo("150");
            verify(transactionRepository).save(txnCaptor.capture());
            assertThat(txnCaptor.getValue().getCostPerUnit()).isNotNull();
        }

        @Test @DisplayName("with cost — updates WAC")
        void withCost() {
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(flour));
            when(ingredientRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            inventoryService.addStock(1L, new BigDecimal("50"), new BigDecimal("6000"), "Delivery", "admin");

            assertThat(flour.getCurrentStock()).isEqualByComparingTo("150");
            assertThat(flour.getWeightedAverageCost()).isNotNull();
            verify(transactionRepository).save(txnCaptor.capture());
            assertThat(txnCaptor.getValue().getCostPerUnit()).isEqualByComparingTo("6000");
        }
    }

    @Nested @DisplayName("adjustStock")
    class AdjustStockTests {
        @Test @DisplayName("increase")
        void increase() {
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(flour));
            when(ingredientRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            inventoryService.adjustStock(1L, new BigDecimal("120"), "Found extra stock", "admin");

            assertThat(flour.getCurrentStock()).isEqualByComparingTo("120");
            verify(transactionRepository).save(txnCaptor.capture());
            assertThat(txnCaptor.getValue().getBalanceBefore()).isEqualByComparingTo("100");
            assertThat(txnCaptor.getValue().getBalanceAfter()).isEqualByComparingTo("120");
        }

        @Test @DisplayName("decrease")
        void decrease() {
            when(ingredientRepository.findById(1L)).thenReturn(Optional.of(flour));
            when(ingredientRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            inventoryService.adjustStock(1L, new BigDecimal("80"), "Spoilage", "admin");

            assertThat(flour.getCurrentStock()).isEqualByComparingTo("80");
        }
    }

    @Nested @DisplayName("Delegation methods")
    class DelegationTests {
        @Test @DisplayName("getLowStockIngredients")
        void lowStock() {
            when(ingredientRepository.findLowStockIngredients(1L)).thenReturn(List.of(flour));
            assertThat(inventoryService.getLowStockIngredients(1L)).hasSize(1);
        }

        @Test @DisplayName("getIngredientsNeedingReorder")
        void reorder() {
            when(ingredientRepository.findIngredientsNeedingReorder(1L)).thenReturn(List.of(flour));
            assertThat(inventoryService.getIngredientsNeedingReorder(1L)).hasSize(1);
        }

        @Test @DisplayName("getTransactionHistory")
        void txnHistory() {
            when(transactionRepository.findByIngredientIdOrderByCreatedAtDesc(1L)).thenReturn(List.of());
            assertThat(inventoryService.getTransactionHistory(1L)).isEmpty();
        }
    }

    @Nested @DisplayName("canMakeProduct / getMissingIngredients")
    class ProductFeasibilityTests {
        @Test @DisplayName("canMakeProduct — available")
        void canMake() {
            when(productIngredientRepository.findByProductIdWithIngredients(1L)).thenReturn(List.of(recipeFlour));
            assertThat(inventoryService.canMakeProduct(1L, 1)).isTrue();
        }

        @Test @DisplayName("canMakeProduct — insufficient")
        void cannotMake() {
            flour.setCurrentStock(new BigDecimal("0.1"));
            when(productIngredientRepository.findByProductIdWithIngredients(1L)).thenReturn(List.of(recipeFlour));
            assertThat(inventoryService.canMakeProduct(1L, 1)).isFalse();
        }

        @Test @DisplayName("getMissingIngredients — lists shortages")
        void missing() {
            flour.setCurrentStock(new BigDecimal("0.1"));
            when(productIngredientRepository.findByProductIdWithIngredients(1L)).thenReturn(List.of(recipeFlour));
            List<String> missing = inventoryService.getMissingIngredients(1L, 1);
            assertThat(missing).hasSize(1);
            assertThat(missing.get(0)).contains("Flour");
        }
    }
}
