package com.elcafe.modules.inventory.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.StockCountItem.VarianceReason;
import com.elcafe.modules.inventory.entity.StockVarianceHistory;
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
class StockVarianceHistoryRepositoryTest {

    @Autowired private StockVarianceHistoryRepository repo;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private Ingredient ingredient;
    private StockVarianceHistory record1;
    private StockVarianceHistory record2;
    private StockVarianceHistory record3;

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

        // Record 1: SPOILAGE, 2026-03-01
        record1 = StockVarianceHistory.builder()
                .restaurant(restaurant)
                .ingredient(ingredient)
                .varianceDate(LocalDate.of(2026, 3, 1))
                .systemQuantity(new BigDecimal("100.000"))
                .actualQuantity(new BigDecimal("95.000"))
                .varianceQuantity(new BigDecimal("-5.000"))
                .variancePercentage(new BigDecimal("5.00"))
                .varianceValue(new BigDecimal("-25.00"))
                .varianceReason(VarianceReason.SPOILAGE)
                .adjustmentMade(true)
                .build();
        em.persist(record1);

        // Record 2: THEFT, 2026-03-15
        record2 = StockVarianceHistory.builder()
                .restaurant(restaurant)
                .ingredient(ingredient)
                .varianceDate(LocalDate.of(2026, 3, 15))
                .systemQuantity(new BigDecimal("80.000"))
                .actualQuantity(new BigDecimal("72.000"))
                .varianceQuantity(new BigDecimal("-8.000"))
                .variancePercentage(new BigDecimal("10.00"))
                .varianceValue(new BigDecimal("-40.00"))
                .varianceReason(VarianceReason.THEFT)
                .adjustmentMade(false)
                .build();
        em.persist(record2);

        // Record 3: SPOILAGE, 2026-04-01
        record3 = StockVarianceHistory.builder()
                .restaurant(restaurant)
                .ingredient(ingredient)
                .varianceDate(LocalDate.of(2026, 4, 1))
                .systemQuantity(new BigDecimal("60.000"))
                .actualQuantity(new BigDecimal("58.000"))
                .varianceQuantity(new BigDecimal("-2.000"))
                .variancePercentage(new BigDecimal("3.33"))
                .varianceValue(new BigDecimal("-10.00"))
                .varianceReason(VarianceReason.SPOILAGE)
                .adjustmentMade(false)
                .build();
        em.persist(record3);

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("findByRestaurant_IdAndDateRange — returns records within date range")
    void findByRestaurantIdAndDateRange() {
        List<StockVarianceHistory> result = repo.findByRestaurant_IdAndDateRange(
                restaurant.getId(),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 31));

        assertEquals(2, result.size());
        // Ordered by varianceDate DESC
        assertEquals(record2.getId(), result.get(0).getId());
        assertEquals(record1.getId(), result.get(1).getId());
    }

    @Test
    @DisplayName("findByIngredientIdAndDateRange — returns records for ingredient within date range")
    void findByIngredientIdAndDateRange() {
        List<StockVarianceHistory> result = repo.findByIngredientIdAndDateRange(
                ingredient.getId(),
                LocalDate.of(2026, 3, 10),
                LocalDate.of(2026, 4, 5));

        assertEquals(2, result.size());
        // Ordered by varianceDate DESC
        assertEquals(record3.getId(), result.get(0).getId());
        assertEquals(record2.getId(), result.get(1).getId());
    }

    @Test
    @DisplayName("findByRestaurant_IdAndVarianceReason — filters by VarianceReason enum")
    void findByRestaurantIdAndVarianceReason() {
        List<StockVarianceHistory> spoilage = repo.findByRestaurant_IdAndVarianceReason(
                restaurant.getId(), VarianceReason.SPOILAGE);

        assertEquals(2, spoilage.size());
        assertTrue(spoilage.stream().allMatch(
                r -> r.getVarianceReason() == VarianceReason.SPOILAGE));

        List<StockVarianceHistory> theft = repo.findByRestaurant_IdAndVarianceReason(
                restaurant.getId(), VarianceReason.THEFT);

        assertEquals(1, theft.size());
        assertEquals(VarianceReason.THEFT, theft.get(0).getVarianceReason());
    }

    @Test
    @DisplayName("getTotalVarianceValueByDateRange — sums varianceValue within date range")
    void getTotalVarianceValueByDateRange() {
        BigDecimal total = repo.getTotalVarianceValueByDateRange(
                restaurant.getId(),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 4, 30));

        // -25.00 + -40.00 + -10.00 = -75.00
        assertEquals(0, new BigDecimal("-75.00").compareTo(total));
    }

    @Test
    @DisplayName("getVarianceBreakdownByReason — groups by reason with count and sum")
    void getVarianceBreakdownByReason() {
        List<Object[]> breakdown = repo.getVarianceBreakdownByReason(
                restaurant.getId(),
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 4, 30));

        assertEquals(2, breakdown.size());

        // Find SPOILAGE row and THEFT row
        Object[] spoilageRow = breakdown.stream()
                .filter(row -> row[0] == VarianceReason.SPOILAGE)
                .findFirst().orElseThrow();
        Object[] theftRow = breakdown.stream()
                .filter(row -> row[0] == VarianceReason.THEFT)
                .findFirst().orElseThrow();

        assertEquals(2L, spoilageRow[1]);
        assertEquals(0, new BigDecimal("-35.00").compareTo((BigDecimal) spoilageRow[2]));

        assertEquals(1L, theftRow[1]);
        assertEquals(0, new BigDecimal("-40.00").compareTo((BigDecimal) theftRow[2]));
    }
}
