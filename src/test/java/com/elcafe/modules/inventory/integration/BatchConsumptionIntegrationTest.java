package com.elcafe.modules.inventory.integration;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.inventory.entity.BatchConsumption;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryBatch;
import com.elcafe.modules.inventory.enums.ValuationMethod;
import com.elcafe.modules.inventory.repository.BatchConsumptionRepository;
import com.elcafe.modules.inventory.repository.InventoryBatchRepository;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class BatchConsumptionIntegrationTest {

    @Autowired private InventoryIngredientRepository ingredientRepository;
    @Autowired private InventoryBatchRepository batchRepository;
    @Autowired private BatchConsumptionRepository consumptionRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private Ingredient ingredient;
    private InventoryBatch batch1;
    private InventoryBatch batch2;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);

        ingredient = Ingredient.builder()
                .restaurant(restaurant)
                .name("Flour")
                .unit("kg")
                .currentStock(new BigDecimal("100.000"))
                .minimumStock(new BigDecimal("10.000"))
                .reorderLevel(new BigDecimal("20.000"))
                .costPerUnit(new BigDecimal("5000.00"))
                .active(true)
                .trackInventory(true)
                .trackExpiry(true)
                .build();
        em.persist(ingredient);

        // Batch 1: older, cheaper — FIFO consumes first
        batch1 = InventoryBatch.builder()
                .ingredient(ingredient)
                .batchNumber("BATCH-OLD")
                .quantity(new BigDecimal("30.000"))
                .initialQuantity(new BigDecimal("30.000"))
                .receivedDate(LocalDate.now().minusDays(20))
                .expiryDate(LocalDate.now().plusDays(10))
                .costPerUnit(new BigDecimal("4500.0000"))
                .status(InventoryBatch.Status.ACTIVE)
                .build();
        em.persist(batch1);

        // Batch 2: newer, more expensive — FIFO consumes second
        batch2 = InventoryBatch.builder()
                .ingredient(ingredient)
                .batchNumber("BATCH-NEW")
                .quantity(new BigDecimal("70.000"))
                .initialQuantity(new BigDecimal("70.000"))
                .receivedDate(LocalDate.now().minusDays(5))
                .expiryDate(LocalDate.now().plusDays(25))
                .costPerUnit(new BigDecimal("5500.0000"))
                .status(InventoryBatch.Status.ACTIVE)
                .build();
        em.persist(batch2);

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("FIFO consumption: consumes older batch first")
    void fifoConsumption() {
        Ingredient ing = ingredientRepository.findById(ingredient.getId()).orElseThrow();
        InventoryBatch oldBatch = batchRepository.findById(batch1.getId()).orElseThrow();
        InventoryBatch newBatch = batchRepository.findById(batch2.getId()).orElseThrow();

        // Consume 40 kg — should take 30 from batch1 (depletes) + 10 from batch2
        BigDecimal consumed1 = oldBatch.consume(new BigDecimal("30.000"));
        batchRepository.save(oldBatch);

        BigDecimal consumed2 = newBatch.consume(new BigDecimal("10.000"));
        batchRepository.save(newBatch);

        // Record FIFO consumption
        BatchConsumption bc1 = BatchConsumption.fromBatch(oldBatch, consumed1, ValuationMethod.FIFO, 1L);

        consumptionRepository.save(bc1);

        BatchConsumption bc2 = BatchConsumption.fromBatch(newBatch, consumed2, ValuationMethod.FIFO, 1L);

        consumptionRepository.save(bc2);

        em.flush();
        em.clear();

        // Verify batch quantities
        InventoryBatch reloadedOld = batchRepository.findById(batch1.getId()).orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(reloadedOld.getQuantity()));
        assertEquals(InventoryBatch.Status.DEPLETED, reloadedOld.getStatus());

        InventoryBatch reloadedNew = batchRepository.findById(batch2.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("60.000").compareTo(reloadedNew.getQuantity()));
        assertEquals(InventoryBatch.Status.ACTIVE, reloadedNew.getStatus());
    }

    @Test
    @DisplayName("COGS recorded with batch-level costs")
    void cogsRecorded() {
        InventoryBatch b1 = batchRepository.findById(batch1.getId()).orElseThrow();

        BatchConsumption bc = BatchConsumption.fromBatch(b1, new BigDecimal("10.000"), ValuationMethod.FIFO, 100L);

        consumptionRepository.save(bc);
        em.flush();
        em.clear();

        List<BatchConsumption> consumptions = consumptionRepository.findByOrderId(100L);
        assertEquals(1, consumptions.size());
        assertEquals(0, new BigDecimal("4500.0000").compareTo(consumptions.get(0).getCostPerUnit()));
        assertEquals(0, new BigDecimal("45000.0000").compareTo(consumptions.get(0).getTotalCost()));
        assertEquals(ValuationMethod.FIFO, consumptions.get(0).getValuationMethod());
    }

    @Test
    @DisplayName("Multiple orders track separate COGS")
    void separateCogsByOrder() {
        InventoryBatch b1 = batchRepository.findById(batch1.getId()).orElseThrow();
        InventoryBatch b2 = batchRepository.findById(batch2.getId()).orElseThrow();

        // Order 1 uses batch 1
        BatchConsumption bc1 = BatchConsumption.fromBatch(b1, new BigDecimal("5.000"), ValuationMethod.FIFO, 101L);

        consumptionRepository.save(bc1);

        // Order 2 uses batch 2
        BatchConsumption bc2 = BatchConsumption.fromBatch(b2, new BigDecimal("8.000"), ValuationMethod.FIFO, 102L);

        consumptionRepository.save(bc2);

        em.flush();
        em.clear();

        List<BatchConsumption> order1 = consumptionRepository.findByOrderId(101L);
        List<BatchConsumption> order2 = consumptionRepository.findByOrderId(102L);
        assertEquals(1, order1.size());
        assertEquals(1, order2.size());
        // Different costs from different batches
        assertEquals(0, new BigDecimal("4500.0000").compareTo(order1.get(0).getCostPerUnit()));
        assertEquals(0, new BigDecimal("5500.0000").compareTo(order2.get(0).getCostPerUnit()));
    }

    @Test
    @DisplayName("Batch depletion updates status to DEPLETED")
    void batchDepletion() {
        InventoryBatch b1 = batchRepository.findById(batch1.getId()).orElseThrow();

        // Consume entire batch
        b1.consume(new BigDecimal("30.000"));
        batchRepository.save(b1);
        em.flush();
        em.clear();

        InventoryBatch reloaded = batchRepository.findById(batch1.getId()).orElseThrow();
        assertEquals(InventoryBatch.Status.DEPLETED, reloaded.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(reloaded.getQuantity()));
    }
}
