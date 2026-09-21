package com.elcafe.modules.inventory.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.inventory.entity.BatchConsumption;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryBatch;
import com.elcafe.modules.inventory.enums.ValuationMethod;
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
class BatchConsumptionRepositoryTest {

    @Autowired private BatchConsumptionRepository consumptionRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private Ingredient ingredient;
    private InventoryBatch batch;

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
                .active(true).trackInventory(true).build();
        em.persist(ingredient);

        batch = InventoryBatch.builder()
                .ingredient(ingredient).batchNumber("BATCH-001")
                .quantity(new BigDecimal("50")).initialQuantity(new BigDecimal("50"))
                .receivedDate(LocalDate.now()).costPerUnit(new BigDecimal("5000"))
                .status(InventoryBatch.Status.ACTIVE).build();
        em.persist(batch);

        // Consumption for order 100
        em.persist(BatchConsumption.builder()
                .ingredient(ingredient).batch(batch)
                .quantity(new BigDecimal("10")).costPerUnit(new BigDecimal("5000"))
                .totalCost(new BigDecimal("50000"))
                .valuationMethod(ValuationMethod.FIFO)
                .orderId(100L).consumedAt(LocalDateTime.now().minusDays(5))
                .batchNumber("BATCH-001").build());

        // Consumption for order 101
        em.persist(BatchConsumption.builder()
                .ingredient(ingredient).batch(batch)
                .quantity(new BigDecimal("5")).costPerUnit(new BigDecimal("5000"))
                .totalCost(new BigDecimal("25000"))
                .valuationMethod(ValuationMethod.FIFO)
                .orderId(101L).consumedAt(LocalDateTime.now().minusDays(2))
                .batchNumber("BATCH-001").build());

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("calculateOrderCOGS — sums total cost for an order")
    void orderCOGS() {
        BigDecimal cogs = consumptionRepository.calculateOrderCOGS(100L);
        assertEquals(0, new BigDecimal("50000").compareTo(cogs));
    }

    @Test
    @DisplayName("calculateTotalCOGS — aggregates across orders in date range")
    void totalCOGS() {
        BigDecimal total = consumptionRepository.calculateTotalCOGS(
                restaurant.getId(),
                LocalDateTime.now().minusDays(10),
                LocalDateTime.now());
        assertEquals(0, new BigDecimal("75000").compareTo(total));
    }

    @Test
    @DisplayName("findByOrderId — returns consumptions for specific order")
    void findByOrderId() {
        List<BatchConsumption> consumptions = consumptionRepository.findByOrderId(100L);
        assertEquals(1, consumptions.size());
        assertEquals(0, new BigDecimal("10").compareTo(consumptions.get(0).getQuantity()));
    }
}
