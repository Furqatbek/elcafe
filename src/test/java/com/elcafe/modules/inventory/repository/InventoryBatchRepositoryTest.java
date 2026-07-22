package com.elcafe.modules.inventory.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryBatch;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class InventoryBatchRepositoryTest {

    @Autowired private InventoryBatchRepository batchRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private Ingredient ingredient;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);

        ingredient = Ingredient.builder()
                .restaurant(restaurant).name("Flour").unit("kg")
                .currentStock(new BigDecimal("100")).minimumStock(BigDecimal.TEN)
                .reorderLevel(new BigDecimal("20"))
                .active(true).trackInventory(true).trackExpiry(true)
                .build();
        em.persist(ingredient);

        // Batch 1: expires soonest
        em.persist(InventoryBatch.builder()
                .ingredient(ingredient).batchNumber("B-SOON")
                .quantity(new BigDecimal("20")).initialQuantity(new BigDecimal("20"))
                .receivedDate(LocalDate.now().minusDays(25))
                .expiryDate(LocalDate.now().plusDays(5))
                .costPerUnit(new BigDecimal("4500"))
                .status(InventoryBatch.Status.ACTIVE).build());

        // Batch 2: expires later
        em.persist(InventoryBatch.builder()
                .ingredient(ingredient).batchNumber("B-LATER")
                .quantity(new BigDecimal("30")).initialQuantity(new BigDecimal("30"))
                .receivedDate(LocalDate.now().minusDays(10))
                .expiryDate(LocalDate.now().plusDays(20))
                .costPerUnit(new BigDecimal("5500"))
                .status(InventoryBatch.Status.ACTIVE).build());

        // Batch 3: already expired
        em.persist(InventoryBatch.builder()
                .ingredient(ingredient).batchNumber("B-EXPIRED")
                .quantity(new BigDecimal("10")).initialQuantity(new BigDecimal("10"))
                .receivedDate(LocalDate.now().minusDays(40))
                .expiryDate(LocalDate.now().minusDays(1))
                .costPerUnit(new BigDecimal("4000"))
                .status(InventoryBatch.Status.ACTIVE).build());

        // Batch 4: depleted
        em.persist(InventoryBatch.builder()
                .ingredient(ingredient).batchNumber("B-DEPLETED")
                .quantity(BigDecimal.ZERO).initialQuantity(new BigDecimal("50"))
                .receivedDate(LocalDate.now().minusDays(30))
                .expiryDate(LocalDate.now().plusDays(1))
                .status(InventoryBatch.Status.DEPLETED).build());

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("findActiveBatchesFEFO — returns FEFO order, excludes depleted")
    void activeBatchesFEFO() {
        List<InventoryBatch> batches = batchRepository.findActiveBatchesFEFO(ingredient.getId());
        // B-EXPIRED (expired but still ACTIVE status), B-SOON, B-LATER — all have qty > 0 and ACTIVE status
        assertEquals(3, batches.size());
        // FEFO: earliest expiry first
        assertEquals("B-EXPIRED", batches.get(0).getBatchNumber());
        assertEquals("B-SOON", batches.get(1).getBatchNumber());
        assertEquals("B-LATER", batches.get(2).getBatchNumber());
    }

    @Test
    @DisplayName("findExpiringBatches — returns batches expiring within threshold")
    void expiringBatches() {
        LocalDate threshold = LocalDate.now().plusDays(7);
        List<InventoryBatch> batches = batchRepository.findExpiringBatches(restaurant.getId(), threshold);
        // B-SOON (expires in 5 days) qualifies, B-EXPIRED is < today so also qualifies
        assertTrue(batches.size() >= 1);
        assertTrue(batches.stream().anyMatch(b -> b.getBatchNumber().equals("B-SOON")));
    }

    @Test
    @DisplayName("findExpiredBatches — returns only past-expiry active batches")
    void expiredBatches() {
        List<InventoryBatch> batches = batchRepository.findExpiredBatches(restaurant.getId(), LocalDate.now());
        assertEquals(1, batches.size());
        assertEquals("B-EXPIRED", batches.get(0).getBatchNumber());
    }

    @Test
    @DisplayName("getEffectiveQuantity — sums only active non-expired batches")
    void effectiveQuantity() {
        BigDecimal effective = batchRepository.getEffectiveQuantity(ingredient.getId(), LocalDate.now());
        // B-SOON (20) + B-LATER (30) = 50 — B-EXPIRED excluded (past expiry), B-DEPLETED excluded (DEPLETED status)
        assertEquals(0, new BigDecimal("50").compareTo(effective));
    }
}
