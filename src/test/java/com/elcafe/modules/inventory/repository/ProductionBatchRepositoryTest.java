package com.elcafe.modules.inventory.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.inventory.entity.ProductionBatch;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.ProductStatus;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class ProductionBatchRepositoryTest {

    @Autowired private ProductionBatchRepository repository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private Product product;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("Test Address");
        restaurant.setPhone("123456789");
        em.persist(restaurant);

        // Minimal Product setup — need a category
        var category = new com.elcafe.modules.menu.entity.Category();
        category.setName("Soups");
        category.setRestaurant(restaurant);
        category.setSortOrder(0);
        em.persist(category);

        product = new Product();
        product.setName("Shurva");
        product.setCategory(category);
        product.setPrice(new BigDecimal("25000"));
        product.setStatus(ProductStatus.ACTIVE);
        product.setUsesProductionBatch(true);
        em.persist(product);

        em.flush();
        em.clear();
    }

    private ProductionBatch createBatch(String number, String name, ProductionBatch.Status status,
                                         BigDecimal output, BigDecimal remaining,
                                         BigDecimal cost, LocalDateTime expiresAt, LocalDateTime completedAt) {
        ProductionBatch batch = ProductionBatch.builder()
                .restaurant(em.find(Restaurant.class, restaurant.getId()))
                .product(em.find(Product.class, product.getId()))
                .batchNumber(number).name(name).outputUnit("L")
                .outputQuantity(output).remainingQuantity(remaining)
                .totalInputCost(cost)
                .costPerUnit(output.compareTo(BigDecimal.ZERO) > 0
                        ? cost.divide(output, 4, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO)
                .status(status).expiresAt(expiresAt).completedAt(completedAt)
                .build();
        em.persist(batch);
        return batch;
    }

    @Test @DisplayName("findAvailableByProduct — returns READY/SERVING with remaining > 0 ordered by expiry")
    void findAvailableByProduct() {
        LocalDateTime now = LocalDateTime.now();
        createBatch("PB-001", "Batch A", ProductionBatch.Status.READY,
                new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("200000"),
                now.plusHours(8), now.minusHours(2));
        createBatch("PB-002", "Batch B", ProductionBatch.Status.SERVING,
                new BigDecimal("10"), new BigDecimal("3"), new BigDecimal("200000"),
                now.plusHours(4), now.minusHours(4)); // expires sooner — should be first
        createBatch("PB-003", "Batch C", ProductionBatch.Status.DEPLETED,
                new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("200000"),
                now.plusHours(6), now.minusHours(3));
        createBatch("PB-004", "Batch D", ProductionBatch.Status.DRAFT,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null);
        em.flush();
        em.clear();

        List<ProductionBatch> available = repository.findAvailableByProduct(product.getId());

        assertThat(available).hasSize(2);
        assertThat(available.get(0).getBatchNumber()).isEqualTo("PB-002"); // earliest expiry first
        assertThat(available.get(1).getBatchNumber()).isEqualTo("PB-001");
    }

    @Test @DisplayName("findByRestaurantIdAndStatus — filters correctly")
    void findByRestaurantIdAndStatus() {
        LocalDateTime now = LocalDateTime.now();
        createBatch("PB-010", "Ready 1", ProductionBatch.Status.READY,
                new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("100000"),
                now.plusHours(8), now);
        createBatch("PB-011", "Draft 1", ProductionBatch.Status.DRAFT,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        createBatch("PB-012", "Ready 2", ProductionBatch.Status.READY,
                new BigDecimal("5"), new BigDecimal("5"), new BigDecimal("50000"),
                now.plusHours(8), now);
        em.flush();
        em.clear();

        List<ProductionBatch> readyBatches = repository.findByRestaurantIdAndStatus(
                restaurant.getId(), ProductionBatch.Status.READY);

        assertThat(readyBatches).hasSize(2);
    }

    @Test @DisplayName("getAverageCostPerUnit — calculates across completed batches")
    void getAverageCostPerUnit() {
        LocalDateTime now = LocalDateTime.now();
        // Batch 1: 200000 / 10 = 20000 per unit
        createBatch("PB-020", "Batch 1", ProductionBatch.Status.READY,
                new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("200000"),
                now.plusHours(8), now);
        // Batch 2: 150000 / 10 = 15000 per unit
        createBatch("PB-021", "Batch 2", ProductionBatch.Status.SERVING,
                new BigDecimal("10"), new BigDecimal("3"), new BigDecimal("150000"),
                now.plusHours(4), now);
        // DRAFT batch — should be excluded
        createBatch("PB-022", "Draft", ProductionBatch.Status.DRAFT,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        em.flush();
        em.clear();

        BigDecimal avgCost = repository.getAverageCostPerUnit(product.getId());

        // Average: (200000 + 150000) / (10 + 10) = 350000 / 20 = 17500
        assertThat(avgCost).isEqualByComparingTo("17500");
    }

    @Test @DisplayName("findActiveBatches — returns READY/SERVING for restaurant")
    void findActiveBatches() {
        LocalDateTime now = LocalDateTime.now();
        createBatch("PB-030", "Active 1", ProductionBatch.Status.READY,
                new BigDecimal("10"), new BigDecimal("8"), new BigDecimal("200000"),
                now.plusHours(8), now);
        createBatch("PB-031", "Active 2", ProductionBatch.Status.SERVING,
                new BigDecimal("10"), new BigDecimal("2"), new BigDecimal("200000"),
                now.plusHours(4), now);
        createBatch("PB-032", "Depleted", ProductionBatch.Status.DEPLETED,
                new BigDecimal("10"), BigDecimal.ZERO, new BigDecimal("200000"),
                now.plusHours(6), now);
        em.flush();
        em.clear();

        List<ProductionBatch> active = repository.findActiveBatches(restaurant.getId());

        assertThat(active).hasSize(2);
    }

    @Test @DisplayName("existsByBatchNumber — returns true for existing batch number")
    void existsByBatchNumber() {
        createBatch("PB-UNIQUE-001", "Test", ProductionBatch.Status.DRAFT,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        em.flush();
        em.clear();

        assertThat(repository.existsByBatchNumber("PB-UNIQUE-001")).isTrue();
        assertThat(repository.existsByBatchNumber("PB-NONEXISTENT")).isFalse();
    }
}
