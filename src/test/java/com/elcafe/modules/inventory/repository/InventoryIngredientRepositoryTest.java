package com.elcafe.modules.inventory.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.Supplier;
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
class InventoryIngredientRepositoryTest {

    @Autowired private InventoryIngredientRepository ingredientRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);

        Supplier supplier = Supplier.builder()
                .restaurant(restaurant).name("Fresh Foods").code("FF001").active(true).build();
        em.persist(supplier);

        // Low stock ingredient (currentStock <= minimumStock)
        em.persist(Ingredient.builder()
                .restaurant(restaurant).name("Flour").unit("kg")
                .currentStock(new BigDecimal("8")).minimumStock(new BigDecimal("10"))
                .reorderLevel(new BigDecimal("20"))
                .active(true).trackInventory(true).supplierEntity(supplier).build());

        // Needs reorder (currentStock <= reorderLevel)
        em.persist(Ingredient.builder()
                .restaurant(restaurant).name("Sugar").unit("kg")
                .currentStock(new BigDecimal("15")).minimumStock(new BigDecimal("5"))
                .reorderLevel(new BigDecimal("20"))
                .active(true).trackInventory(true).supplierEntity(supplier).build());

        // Well stocked — neither low nor needs reorder
        em.persist(Ingredient.builder()
                .restaurant(restaurant).name("Salt").unit("kg")
                .currentStock(new BigDecimal("100")).minimumStock(new BigDecimal("5"))
                .reorderLevel(new BigDecimal("10"))
                .active(true).trackInventory(true).build());

        // Inactive ingredient
        em.persist(Ingredient.builder()
                .restaurant(restaurant).name("Pepper").unit("g")
                .currentStock(new BigDecimal("2")).minimumStock(new BigDecimal("10"))
                .reorderLevel(new BigDecimal("20"))
                .active(false).trackInventory(true).build());

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("findLowStockIngredients — returns only active ingredients below minimum")
    void lowStock() {
        List<Ingredient> result = ingredientRepository.findLowStockIngredients(restaurant.getId());
        assertEquals(1, result.size());
        assertEquals("Flour", result.get(0).getName());
    }

    @Test
    @DisplayName("findIngredientsNeedingReorder — returns active below reorder level")
    void needsReorder() {
        List<Ingredient> result = ingredientRepository.findIngredientsNeedingReorder(restaurant.getId());
        // Flour (8 <= 20) and Sugar (15 <= 20) — both below reorder level
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("findByRestaurantIdWithSupplier — JOIN FETCH avoids N+1")
    void withSupplier() {
        List<Ingredient> result = ingredientRepository.findByRestaurantIdWithSupplier(restaurant.getId());
        // All 4 ingredients (including inactive) — it's a LEFT JOIN FETCH
        assertEquals(4, result.size());
        // Flour and Sugar have supplier, Salt and Pepper don't
        long withSupplier = result.stream().filter(i -> i.getSupplierEntity() != null).count();
        assertEquals(2, withSupplier);
    }

    @Test
    @DisplayName("findByRestaurant_IdAndActiveTrue — excludes inactive")
    void activeOnly() {
        List<Ingredient> result = ingredientRepository.findByRestaurant_IdAndActiveTrue(restaurant.getId());
        assertEquals(3, result.size());
        assertTrue(result.stream().noneMatch(i -> i.getName().equals("Pepper")));
    }
}
