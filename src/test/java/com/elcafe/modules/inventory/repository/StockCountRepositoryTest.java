package com.elcafe.modules.inventory.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.inventory.entity.StockCount;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class StockCountRepositoryTest {

    @Autowired private StockCountRepository stockCountRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);

        em.persist(StockCount.builder()
                .restaurant(restaurant).countNumber("SC-20260401-0001")
                .countType(StockCount.CountType.FULL)
                .status(StockCount.Status.DRAFT)
                .totalItems(0).countedItems(0).varianceCount(0)
                .totalVarianceValue(BigDecimal.ZERO).build());

        em.persist(StockCount.builder()
                .restaurant(restaurant).countNumber("SC-20260402-0001")
                .countType(StockCount.CountType.CYCLE)
                .status(StockCount.Status.IN_PROGRESS)
                .totalItems(0).countedItems(0).varianceCount(0)
                .totalVarianceValue(BigDecimal.ZERO).build());

        em.persist(StockCount.builder()
                .restaurant(restaurant).countNumber("SC-20260403-0001")
                .countType(StockCount.CountType.FULL)
                .status(StockCount.Status.APPROVED)
                .totalItems(0).countedItems(0).varianceCount(0)
                .totalVarianceValue(BigDecimal.ZERO).build());

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("findActiveStockCounts — returns DRAFT, IN_PROGRESS, PENDING_REVIEW only")
    void activeStockCounts() {
        List<StockCount> result = stockCountRepository.findActiveStockCounts(restaurant.getId());
        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(sc -> sc.getStatus() == StockCount.Status.APPROVED));
    }

    @Test
    @DisplayName("findByRestaurant_Id — returns all stock counts")
    void allByRestaurant() {
        List<StockCount> result = stockCountRepository.findByRestaurant_Id(restaurant.getId());
        assertEquals(3, result.size());
    }
}
