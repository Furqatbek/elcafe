package com.elcafe.modules.inventory.integration;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.StockCount;
import com.elcafe.modules.inventory.entity.StockCountItem;
import com.elcafe.modules.inventory.entity.StockVarianceHistory;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.StockCountItemRepository;
import com.elcafe.modules.inventory.repository.StockCountRepository;
import com.elcafe.modules.inventory.repository.StockVarianceHistoryRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class StockCountIntegrationTest {

    @Autowired private StockCountRepository stockCountRepository;
    @Autowired private StockCountItemRepository stockCountItemRepository;
    @Autowired private StockVarianceHistoryRepository varianceHistoryRepository;
    @Autowired private InventoryIngredientRepository ingredientRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private Ingredient flour;
    private Ingredient sugar;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);

        flour = Ingredient.builder()
                .restaurant(restaurant)
                .name("Flour")
                .unit("kg")
                .currentStock(new BigDecimal("100.000"))
                .minimumStock(new BigDecimal("10.000"))
                .reorderLevel(new BigDecimal("20.000"))
                .costPerUnit(new BigDecimal("5000.00"))
                .active(true)
                .trackInventory(true)
                .build();
        em.persist(flour);

        sugar = Ingredient.builder()
                .restaurant(restaurant)
                .name("Sugar")
                .unit("kg")
                .currentStock(new BigDecimal("50.000"))
                .minimumStock(new BigDecimal("5.000"))
                .reorderLevel(new BigDecimal("10.000"))
                .costPerUnit(new BigDecimal("8000.00"))
                .active(true)
                .trackInventory(true)
                .build();
        em.persist(sugar);

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Full stock count lifecycle: create → record → approve")
    void fullLifecycle() {
        Ingredient ing1 = ingredientRepository.findById(flour.getId()).orElseThrow();
        Ingredient ing2 = ingredientRepository.findById(sugar.getId()).orElseThrow();

        // Create stock count with items
        StockCount stockCount = StockCount.builder()
                .restaurant(restaurant)
                .countNumber("SC-20260403-0001")
                .countType(StockCount.CountType.FULL)
                .status(StockCount.Status.DRAFT)
                .initiatedBy("admin")
                .totalItems(2)
                .countedItems(0)
                .varianceCount(0)
                .totalVarianceValue(BigDecimal.ZERO)
                .build();

        StockCountItem item1 = StockCountItem.builder()
                .stockCount(stockCount)
                .ingredient(ing1)
                .systemQuantity(ing1.getCurrentStock())
                .status(StockCountItem.Status.PENDING)
                .build();
        StockCountItem item2 = StockCountItem.builder()
                .stockCount(stockCount)
                .ingredient(ing2)
                .systemQuantity(ing2.getCurrentStock())
                .status(StockCountItem.Status.PENDING)
                .build();

        stockCount.setItems(new ArrayList<>(List.of(item1, item2)));
        stockCountRepository.save(stockCount);
        em.flush();
        em.clear();

        // Record counts
        StockCount loaded = stockCountRepository.findById(stockCount.getId()).orElseThrow();
        loaded.setStatus(StockCount.Status.IN_PROGRESS);

        List<StockCountItem> items = loaded.getItems();
        assertEquals(2, items.size());

        // Flour: system=100, counted=95 → variance=-5
        items.get(0).recordCount(new BigDecimal("95.000"), "counter1", "Slight shortage");
        // Sugar: system=50, counted=50 → no variance
        items.get(1).recordCount(new BigDecimal("50.000"), "counter1", null);

        loaded.recalculateTotals();
        stockCountRepository.save(loaded);
        em.flush();
        em.clear();

        // Verify totals
        StockCount afterCount = stockCountRepository.findById(stockCount.getId()).orElseThrow();
        assertEquals(2, afterCount.getCountedItems());
        assertEquals(1, afterCount.getVarianceCount()); // Only flour has variance
    }

    @Test
    @DisplayName("Variance detection: counted vs system quantity")
    void varianceDetection() {
        Ingredient ing = ingredientRepository.findById(flour.getId()).orElseThrow();

        StockCount stockCount = StockCount.builder()
                .restaurant(restaurant)
                .countNumber("SC-VAR-001")
                .countType(StockCount.CountType.SPOT_CHECK)
                .status(StockCount.Status.IN_PROGRESS)
                .totalItems(1).countedItems(0).varianceCount(0)
                .totalVarianceValue(BigDecimal.ZERO)
                .build();

        StockCountItem item = StockCountItem.builder()
                .stockCount(stockCount)
                .ingredient(ing)
                .systemQuantity(new BigDecimal("100.000"))
                .status(StockCountItem.Status.PENDING)
                .build();
        stockCount.setItems(new ArrayList<>(List.of(item)));
        stockCountRepository.save(stockCount);
        em.flush();
        em.clear();

        // Record count with variance
        StockCountItem loaded = stockCountItemRepository.findById(item.getId()).orElseThrow();
        loaded.recordCount(new BigDecimal("92.000"), "counter1", "Missing stock");
        stockCountItemRepository.save(loaded);
        em.flush();
        em.clear();

        StockCountItem reloaded = stockCountItemRepository.findById(item.getId()).orElseThrow();
        assertEquals(StockCountItem.Status.COUNTED, reloaded.getStatus());
        assertEquals(0, new BigDecimal("-8.000").compareTo(reloaded.getVarianceQuantity()));
        assertTrue(reloaded.hasVariance());
    }

    @Test
    @DisplayName("Variance history recorded on approval")
    void varianceHistoryOnApproval() {
        Ingredient ing = ingredientRepository.findById(flour.getId()).orElseThrow();

        StockCount stockCount = StockCount.builder()
                .restaurant(restaurant)
                .countNumber("SC-HIST-001")
                .countType(StockCount.CountType.SPOT_CHECK)
                .status(StockCount.Status.PENDING_REVIEW)
                .totalItems(1).countedItems(1).varianceCount(1)
                .totalVarianceValue(new BigDecimal("40000"))
                .build();

        StockCountItem item = StockCountItem.builder()
                .stockCount(stockCount)
                .ingredient(ing)
                .systemQuantity(new BigDecimal("100.000"))
                .countedQuantity(new BigDecimal("92.000"))
                .varianceQuantity(new BigDecimal("-8.000"))
                .varianceValue(new BigDecimal("40000.00"))
                .varianceReason(StockCountItem.VarianceReason.SHRINKAGE)
                .status(StockCountItem.Status.COUNTED)
                .countedBy("counter1")
                .build();
        stockCount.setItems(new ArrayList<>(List.of(item)));
        stockCountRepository.save(stockCount);
        em.flush();
        em.clear();

        // Create variance history from item
        StockCountItem savedItem = stockCountItemRepository.findById(item.getId()).orElseThrow();
        StockVarianceHistory history = StockVarianceHistory.fromStockCountItem(savedItem);
        history.setRestaurant(restaurant);
        history.setAdjustmentMade(true);
        varianceHistoryRepository.save(history);
        em.flush();
        em.clear();

        // Verify
        List<StockVarianceHistory> histories = varianceHistoryRepository.findAll();
        assertEquals(1, histories.size());
        StockVarianceHistory saved = histories.get(0);
        assertEquals(0, new BigDecimal("100.000").compareTo(saved.getSystemQuantity()));
        assertEquals(0, new BigDecimal("92.000").compareTo(saved.getActualQuantity()));
        assertEquals(0, new BigDecimal("-8.000").compareTo(saved.getVarianceQuantity()));
        assertTrue(saved.getAdjustmentMade());
    }

    @Test
    @DisplayName("Stock count with no variance — all items match system")
    void noVariance() {
        Ingredient ing = ingredientRepository.findById(flour.getId()).orElseThrow();

        StockCount stockCount = StockCount.builder()
                .restaurant(restaurant)
                .countNumber("SC-OK-001")
                .countType(StockCount.CountType.SPOT_CHECK)
                .status(StockCount.Status.IN_PROGRESS)
                .totalItems(1).countedItems(0).varianceCount(0)
                .totalVarianceValue(BigDecimal.ZERO)
                .build();

        StockCountItem item = StockCountItem.builder()
                .stockCount(stockCount)
                .ingredient(ing)
                .systemQuantity(new BigDecimal("100.000"))
                .status(StockCountItem.Status.PENDING)
                .build();
        stockCount.setItems(new ArrayList<>(List.of(item)));
        stockCountRepository.save(stockCount);
        em.flush();
        em.clear();

        // Count matches system exactly
        StockCountItem loaded = stockCountItemRepository.findById(item.getId()).orElseThrow();
        loaded.recordCount(new BigDecimal("100.000"), "counter1", null);
        stockCountItemRepository.save(loaded);

        StockCount sc = stockCountRepository.findById(stockCount.getId()).orElseThrow();
        sc.recalculateTotals();
        stockCountRepository.save(sc);
        em.flush();
        em.clear();

        StockCount reloaded = stockCountRepository.findById(stockCount.getId()).orElseThrow();
        assertEquals(0, reloaded.getVarianceCount());
        assertFalse(reloaded.getItems().get(0).hasVariance());
    }
}
