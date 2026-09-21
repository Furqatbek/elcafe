package com.elcafe.modules.inventory.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.WasteRecord;
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
class WasteRecordRepositoryTest {

    @Autowired private WasteRecordRepository wasteRepository;
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
                .costPerUnit(new BigDecimal("5000"))
                .active(true).trackInventory(true).build();
        em.persist(ingredient);

        // Waste 1: expired, this month
        em.persist(WasteRecord.builder()
                .restaurant(restaurant).ingredient(ingredient)
                .wasteDate(LocalDate.now().minusDays(5))
                .quantity(new BigDecimal("3")).unitCost(new BigDecimal("5000"))
                .totalCost(new BigDecimal("15000"))
                .wasteReason(WasteRecord.WasteReason.EXPIRED)
                .recordedBy("admin").build());

        // Waste 2: spoiled, this month
        em.persist(WasteRecord.builder()
                .restaurant(restaurant).ingredient(ingredient)
                .wasteDate(LocalDate.now().minusDays(2))
                .quantity(new BigDecimal("1")).unitCost(new BigDecimal("5000"))
                .totalCost(new BigDecimal("5000"))
                .wasteReason(WasteRecord.WasteReason.SPOILED)
                .recordedBy("admin").build());

        // Waste 3: old record, last month
        em.persist(WasteRecord.builder()
                .restaurant(restaurant).ingredient(ingredient)
                .wasteDate(LocalDate.now().minusDays(35))
                .quantity(new BigDecimal("2")).unitCost(new BigDecimal("5000"))
                .totalCost(new BigDecimal("10000"))
                .wasteReason(WasteRecord.WasteReason.EXPIRED)
                .recordedBy("admin").build());

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("findByRestaurant_IdAndDateRange — filters by date range")
    void dateRange() {
        List<WasteRecord> result = wasteRepository.findByRestaurant_IdAndDateRange(
                restaurant.getId(), LocalDate.now().minusDays(10), LocalDate.now());
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("findByRestaurant_IdAndReason — filters by reason")
    void byReason() {
        List<WasteRecord> result = wasteRepository.findByRestaurant_IdAndReason(
                restaurant.getId(), WasteRecord.WasteReason.EXPIRED);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("getTotalWasteCost — aggregates cost in date range")
    void totalCost() {
        BigDecimal total = wasteRepository.getTotalWasteCost(
                restaurant.getId(), LocalDate.now().minusDays(10), LocalDate.now());
        assertEquals(0, new BigDecimal("20000").compareTo(total));
    }
}
