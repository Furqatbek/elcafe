package com.elcafe.modules.menu.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class CategoryRepositoryTest {

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test"); restaurant.setAddress("123 St"); restaurant.setActive(true);
        em.persist(restaurant);
        em.persist(Category.builder().restaurant(restaurant).name("Appetizers").sortOrder(0).active(true).build());
        em.persist(Category.builder().restaurant(restaurant).name("Main Course").sortOrder(1).active(true).build());
        em.persist(Category.builder().restaurant(restaurant).name("Hidden").sortOrder(2).active(false).build());
        em.flush(); em.clear();
    }

    @Test @DisplayName("findByRestaurant_IdAndActiveTrueOrderBySortOrder — active only, sorted")
    void activeSorted() {
        List<Category> active = categoryRepository.findByRestaurant_IdAndActiveTrueOrderBySortOrder(restaurant.getId());
        assertEquals(2, active.size());
        assertEquals("Appetizers", active.get(0).getName());
        assertEquals("Main Course", active.get(1).getName());
    }

    @Test @DisplayName("findByRestaurant_IdOrderBySortOrder — all categories sorted")
    void allSorted() {
        List<Category> all = categoryRepository.findByRestaurant_IdOrderBySortOrder(restaurant.getId());
        assertEquals(3, all.size());
    }
}
