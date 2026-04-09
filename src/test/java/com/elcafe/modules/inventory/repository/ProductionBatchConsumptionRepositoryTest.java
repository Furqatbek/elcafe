package com.elcafe.modules.inventory.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.inventory.entity.ProductionBatch;
import com.elcafe.modules.inventory.entity.ProductionBatchConsumption;
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
class ProductionBatchConsumptionRepositoryTest {

    @Autowired private ProductionBatchConsumptionRepository repository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private ProductionBatch batch;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("Test Address");
        restaurant.setPhone("123456789");
        em.persist(restaurant);

        var category = new com.elcafe.modules.menu.entity.Category();
        category.setName("Soups");
        category.setRestaurant(restaurant);
        category.setSortOrder(0);
        em.persist(category);

        Product product = new Product();
        product.setName("Shurva");
        product.setCategory(category);
        product.setPrice(new BigDecimal("25000"));
        product.setStatus(ProductStatus.LIVE);
        em.persist(product);

        batch = ProductionBatch.builder()
                .restaurant(restaurant).product(product)
                .batchNumber("PB-C-001").name("Shurva Batch")
                .outputUnit("L").outputQuantity(new BigDecimal("10"))
                .remainingQuantity(new BigDecimal("7"))
                .totalInputCost(new BigDecimal("300000"))
                .costPerUnit(new BigDecimal("30000"))
                .status(ProductionBatch.Status.SERVING)
                .build();
        em.persist(batch);

        em.flush();
        em.clear();
    }

    private void createConsumption(Long orderId, BigDecimal qty, LocalDateTime at) {
        ProductionBatchConsumption c = ProductionBatchConsumption.builder()
                .productionBatch(em.find(ProductionBatch.class, batch.getId()))
                .orderId(orderId).orderItemId(orderId) // simplify
                .quantity(qty)
                .costPerUnit(new BigDecimal("30000"))
                .totalCost(qty.multiply(new BigDecimal("30000")))
                .consumedAt(at)
                .build();
        em.persist(c);
    }

    @Test @DisplayName("findByOrderId — returns consumptions for an order")
    void findByOrderId() {
        LocalDateTime now = LocalDateTime.now();
        createConsumption(1L, new BigDecimal("0.5"), now);
        createConsumption(1L, new BigDecimal("0.7"), now);
        createConsumption(2L, new BigDecimal("1.0"), now);
        em.flush();
        em.clear();

        List<ProductionBatchConsumption> result = repository.findByOrderId(1L);

        assertThat(result).hasSize(2);
    }

    @Test @DisplayName("calculateOrderCOGS — sums cost for an order")
    void calculateOrderCOGS() {
        LocalDateTime now = LocalDateTime.now();
        createConsumption(5L, new BigDecimal("0.5"), now); // 15000
        createConsumption(5L, new BigDecimal("1.0"), now); // 30000
        em.flush();
        em.clear();

        BigDecimal cogs = repository.calculateOrderCOGS(5L);

        assertThat(cogs).isEqualByComparingTo("45000");
    }

    @Test @DisplayName("getTotalConsumptionCost — sums by restaurant and date range")
    void getTotalConsumptionCost() {
        LocalDateTime now = LocalDateTime.now();
        createConsumption(10L, new BigDecimal("1"), now.minusHours(2));
        createConsumption(11L, new BigDecimal("2"), now.minusHours(1));
        createConsumption(12L, new BigDecimal("0.5"), now.minusDays(5)); // outside range
        em.flush();
        em.clear();

        BigDecimal total = repository.getTotalConsumptionCost(
                restaurant.getId(),
                now.minusHours(3),
                now);

        // Only first two: (1 * 30000) + (2 * 30000) = 90000
        assertThat(total).isEqualByComparingTo("90000");
    }
}
