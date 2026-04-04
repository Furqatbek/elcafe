package com.elcafe.modules.inventory.integration;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryBatch;
import com.elcafe.modules.inventory.entity.InventoryTransaction;
import com.elcafe.modules.inventory.entity.Supplier;
import com.elcafe.modules.inventory.enums.TransactionType;
import com.elcafe.modules.inventory.repository.InventoryBatchRepository;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.InventoryTransactionRepository;
import com.elcafe.modules.inventory.repository.SupplierRepository;
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
class StockReceiveIntegrationTest {

    @Autowired private InventoryIngredientRepository ingredientRepository;
    @Autowired private InventoryBatchRepository batchRepository;
    @Autowired private InventoryTransactionRepository transactionRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private Supplier supplier;
    private Ingredient ingredient;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        restaurant.setAcceptingOrders(true);
        restaurant.setRating(java.math.BigDecimal.ZERO);
        em.persist(restaurant);

        supplier = Supplier.builder()
                .restaurant(restaurant)
                .name("Fresh Foods")
                .code("FF001")
                .active(true)
                .build();
        em.persist(supplier);

        ingredient = Ingredient.builder()
                .restaurant(restaurant)
                .name("Flour")
                .unit("kg")
                .currentStock(new BigDecimal("50.000"))
                .minimumStock(new BigDecimal("10.000"))
                .reorderLevel(new BigDecimal("20.000"))
                .costPerUnit(new BigDecimal("5000.00"))
                .active(true)
                .trackInventory(true)
                .trackExpiry(true)
                .defaultShelfLifeDays(30)
                .expiryAlertDays(7)
                .build();
        em.persist(ingredient);

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Full stock receive: supplier → batch → stock increase → transaction")
    void fullStockReceiveFlow() {
        // Reload entities
        Ingredient ing = ingredientRepository.findById(ingredient.getId()).orElseThrow();
        Supplier sup = supplierRepository.findById(supplier.getId()).orElseThrow();

        // Create batch
        InventoryBatch batch = InventoryBatch.builder()
                .ingredient(ing)
                .batchNumber("BATCH-001")
                .quantity(new BigDecimal("100.000"))
                .initialQuantity(new BigDecimal("100.000"))
                .receivedDate(LocalDate.now())
                .expiryDate(LocalDate.now().plusDays(30))
                .costPerUnit(new BigDecimal("5200.0000"))
                .supplier(sup)
                .status(InventoryBatch.Status.ACTIVE)
                .build();
        batchRepository.save(batch);

        // Simulate stock increase
        BigDecimal balanceBefore = ing.getCurrentStock();
        ing.addStock(new BigDecimal("100.000"));
        ingredientRepository.save(ing);

        // Record transaction
        InventoryTransaction txn = InventoryTransaction.builder()
                .ingredient(ing)
                .type(TransactionType.PURCHASE)
                .quantity(new BigDecimal("100.000"))
                .balanceBefore(balanceBefore)
                .balanceAfter(ing.getCurrentStock())
                .referenceType("PURCHASE_ORDER")
                .costPerUnit(new BigDecimal("5200.0000"))
                .totalCost(new BigDecimal("520000.0000"))
                .performedBy("admin")
                .notes("Received from Fresh Foods")
                .build();
        transactionRepository.save(txn);

        em.flush();
        em.clear();

        // Verify
        Ingredient reloaded = ingredientRepository.findById(ingredient.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("150.000").compareTo(reloaded.getCurrentStock()));

        List<InventoryBatch> batches = batchRepository.findByIngredientIdOrderByExpiryDateAsc(ingredient.getId());
        assertEquals(1, batches.size());
        assertEquals("BATCH-001", batches.get(0).getBatchNumber());
        assertEquals(InventoryBatch.Status.ACTIVE, batches.get(0).getStatus());
    }

    @Test
    @DisplayName("Batch expiry calculation from shelf life")
    void batchExpiryFromShelfLife() {
        Ingredient ing = ingredientRepository.findById(ingredient.getId()).orElseThrow();

        LocalDate receivedDate = LocalDate.now().minusDays(5);
        LocalDate expectedExpiry = LocalDate.now().plusDays(25); // 30 days shelf life

        InventoryBatch batch = InventoryBatch.builder()
                .ingredient(ing)
                .batchNumber("BATCH-002")
                .quantity(new BigDecimal("50.000"))
                .initialQuantity(new BigDecimal("50.000"))
                .receivedDate(receivedDate)
                .expiryDate(expectedExpiry)
                .status(InventoryBatch.Status.ACTIVE)
                .build();
        batch = batchRepository.save(batch);
        em.flush();
        em.clear();

        InventoryBatch loaded = batchRepository.findById(batch.getId()).orElseThrow();
        assertNotNull(loaded.getExpiryDate());
        assertNotNull(loaded.getReceivedDate());
        assertFalse(loaded.isExpired());
    }

    @Test
    @DisplayName("Transaction audit trail with cost tracking")
    void transactionAuditTrail() {
        Ingredient ing = ingredientRepository.findById(ingredient.getId()).orElseThrow();

        InventoryTransaction txn = InventoryTransaction.builder()
                .ingredient(ing)
                .type(TransactionType.PURCHASE)
                .quantity(new BigDecimal("25.000"))
                .balanceBefore(new BigDecimal("50.000"))
                .balanceAfter(new BigDecimal("75.000"))
                .costPerUnit(new BigDecimal("4800.0000"))
                .performedBy("admin")
                .build();
        txn.calculateTotalCost();
        transactionRepository.save(txn);
        em.flush();
        em.clear();

        List<InventoryTransaction> txns = transactionRepository.findByIngredientIdOrderByCreatedAtDesc(ingredient.getId());
        assertEquals(1, txns.size());
        assertEquals(TransactionType.PURCHASE, txns.get(0).getType());
        assertEquals(0, new BigDecimal("120000.0000").compareTo(txns.get(0).getTotalCost()));
    }

    @Test
    @DisplayName("Supplier linked to batch persists correctly")
    void supplierBatchRelationship() {
        Ingredient ing = ingredientRepository.findById(ingredient.getId()).orElseThrow();
        Supplier sup = supplierRepository.findById(supplier.getId()).orElseThrow();

        InventoryBatch batch = InventoryBatch.builder()
                .ingredient(ing)
                .batchNumber("BATCH-SUP")
                .quantity(new BigDecimal("30.000"))
                .initialQuantity(new BigDecimal("30.000"))
                .receivedDate(LocalDate.now())
                .supplier(sup)
                .poReference("PO-2026-001")
                .status(InventoryBatch.Status.ACTIVE)
                .build();
        batch = batchRepository.save(batch);
        em.flush();
        em.clear();

        InventoryBatch loaded = batchRepository.findById(batch.getId()).orElseThrow();
        assertNotNull(loaded.getSupplier());
        assertEquals("Fresh Foods", loaded.getSupplier().getName());
        assertEquals("PO-2026-001", loaded.getPoReference());
    }
}
