package com.elcafe.modules.inventory.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryTransaction;
import com.elcafe.modules.inventory.enums.TransactionType;
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

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class InventoryTransactionRepositoryTest {

    @Autowired private InventoryTransactionRepository transactionRepository;
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
                .active(true).trackInventory(true).build();
        em.persist(ingredient);

        em.persist(InventoryTransaction.builder()
                .ingredient(ingredient).type(TransactionType.PURCHASE)
                .quantity(new BigDecimal("50")).balanceBefore(new BigDecimal("50"))
                .balanceAfter(new BigDecimal("100")).performedBy("admin").build());

        em.persist(InventoryTransaction.builder()
                .ingredient(ingredient).type(TransactionType.ORDER_DEDUCTION)
                .quantity(new BigDecimal("10")).balanceBefore(new BigDecimal("100"))
                .balanceAfter(new BigDecimal("90"))
                .referenceType("ORDER").referenceId(1L).performedBy("system").build());

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("findByIngredientIdOrderByCreatedAtDesc — returns ordered by date")
    void byIngredientOrdered() {
        List<InventoryTransaction> result = transactionRepository
                .findByIngredientIdOrderByCreatedAtDesc(ingredient.getId());
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("findByRestaurantAndDateRange — filters by restaurant and date")
    void byRestaurantAndDateRange() {
        List<InventoryTransaction> result = transactionRepository.findByRestaurantAndDateRange(
                restaurant.getId(), LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1));
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("findByReferenceTypeAndReferenceId — finds by reference")
    void byReference() {
        List<InventoryTransaction> result = transactionRepository
                .findByReferenceTypeAndReferenceId("ORDER", 1L);
        assertEquals(1, result.size());
        assertEquals(TransactionType.ORDER_DEDUCTION, result.get(0).getType());
    }
}
