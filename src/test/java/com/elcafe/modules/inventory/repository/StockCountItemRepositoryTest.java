package com.elcafe.modules.inventory.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.StockCount;
import com.elcafe.modules.inventory.entity.StockCountItem;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class StockCountItemRepositoryTest {

    @Autowired private StockCountItemRepository repo;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private Ingredient ingredient;
    private StockCount stockCount;
    private StockCountItem pendingItem;
    private StockCountItem countedItem;
    private StockCountItem verifiedItem;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("R");
        restaurant.setAddress("A");
        restaurant.setActive(true);
        em.persist(restaurant);

        ingredient = new Ingredient();
        ingredient.setRestaurant(restaurant);
        ingredient.setName("Flour");
        ingredient.setUnit("kg");
        em.persist(ingredient);

        stockCount = StockCount.builder()
                .restaurant(restaurant)
                .countNumber("SC-TEST-001")
                .countType(StockCount.CountType.FULL)
                .status(StockCount.Status.IN_PROGRESS)
                .totalItems(0).countedItems(0).varianceCount(0)
                .totalVarianceValue(BigDecimal.ZERO)
                .build();
        em.persist(stockCount);

        LocalDateTime now = LocalDateTime.now();

        // PENDING item — no variance
        pendingItem = StockCountItem.builder()
                .stockCount(stockCount)
                .ingredient(ingredient)
                .systemQuantity(new BigDecimal("10.000"))
                .status(StockCountItem.Status.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
        em.persist(pendingItem);

        // COUNTED item — has variance
        countedItem = StockCountItem.builder()
                .stockCount(stockCount)
                .ingredient(ingredient)
                .systemQuantity(new BigDecimal("20.000"))
                .countedQuantity(new BigDecimal("17.000"))
                .varianceQuantity(new BigDecimal("-3.000"))
                .varianceValue(new BigDecimal("-15.00"))
                .status(StockCountItem.Status.COUNTED)
                .countedBy("Alice")
                .countedAt(now)
                .varianceReason(StockCountItem.VarianceReason.SPOILAGE)
                .createdAt(now)
                .updatedAt(now)
                .build();
        em.persist(countedItem);

        // VERIFIED item — has variance
        verifiedItem = StockCountItem.builder()
                .stockCount(stockCount)
                .ingredient(ingredient)
                .systemQuantity(new BigDecimal("50.000"))
                .countedQuantity(new BigDecimal("55.000"))
                .varianceQuantity(new BigDecimal("5.000"))
                .varianceValue(new BigDecimal("25.00"))
                .status(StockCountItem.Status.VERIFIED)
                .countedBy("Bob")
                .countedAt(now)
                .varianceReason(StockCountItem.VarianceReason.COUNTING_ERROR)
                .createdAt(now)
                .updatedAt(now)
                .build();
        em.persist(verifiedItem);

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("findItemsWithVariance — returns items with non-null, non-zero variance ordered by ABS(varianceQuantity) DESC")
    void findItemsWithVariance() {
        List<StockCountItem> result = repo.findItemsWithVariance(stockCount.getId());

        assertEquals(2, result.size());
        // ABS(5.000) > ABS(-3.000) => verified first, counted second
        assertEquals(verifiedItem.getId(), result.get(0).getId());
        assertEquals(countedItem.getId(), result.get(1).getId());
    }

    @Test
    @DisplayName("findPendingItems — returns only PENDING status items")
    void findPendingItems() {
        List<StockCountItem> result = repo.findPendingItems(stockCount.getId());

        assertEquals(1, result.size());
        assertEquals(StockCountItem.Status.PENDING, result.get(0).getStatus());
        assertEquals(pendingItem.getId(), result.get(0).getId());
    }

    @Test
    @DisplayName("countCountedItems — counts items with COUNTED status")
    void countCountedItems() {
        int count = repo.countCountedItems(stockCount.getId());

        assertEquals(1, count);
    }

    @Test
    @DisplayName("getTotalVarianceValue — sums all varianceValue for a stock count")
    void getTotalVarianceValue() {
        BigDecimal total = repo.getTotalVarianceValue(stockCount.getId());

        // -15.00 + 25.00 = 10.00
        assertEquals(0, new BigDecimal("10.00").compareTo(total));
    }

    @Test
    @DisplayName("findByIngredientId — returns items ordered by stockCount.createdAt DESC")
    void findByIngredientId() {
        List<StockCountItem> result = repo.findByIngredientId(ingredient.getId());

        assertEquals(3, result.size());
        // All belong to the same ingredient
        assertTrue(result.stream().allMatch(
                item -> item.getIngredient().getId().equals(ingredient.getId())));
    }
}
