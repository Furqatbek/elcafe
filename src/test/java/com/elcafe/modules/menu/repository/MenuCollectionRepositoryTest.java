package com.elcafe.modules.menu.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.menu.entity.MenuCollection;
import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class MenuCollectionRepositoryTest {

    @Autowired private MenuCollectionRepository collectionRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test"); restaurant.setAddress("123 St"); restaurant.setActive(true);
        em.persist(restaurant);
        em.persist(MenuCollection.builder().restaurant(restaurant).name("Active Now").isActive(true)
                .startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 12, 31)).sortOrder(0).build());
        em.persist(MenuCollection.builder().restaurant(restaurant).name("Expired").isActive(true)
                .startDate(LocalDate.of(2025, 1, 1)).endDate(LocalDate.of(2025, 12, 31)).sortOrder(1).build());
        em.persist(MenuCollection.builder().restaurant(restaurant).name("Disabled").isActive(false).sortOrder(2).build());
        em.flush(); em.clear();
    }

    @Test @DisplayName("findActiveMenuCollections — date-based activation")
    void activeByDate() {
        List<MenuCollection> active = collectionRepository.findActiveMenuCollections(
                restaurant.getId(), LocalDate.of(2026, 6, 1));
        assertEquals(1, active.size());
        assertEquals("Active Now", active.get(0).getName());
    }

    @Test @DisplayName("findByRestaurant_Id — returns all for restaurant")
    void allByRestaurant() {
        var page = collectionRepository.findByRestaurant_Id(restaurant.getId(),
                org.springframework.data.domain.PageRequest.of(0, 20));
        assertEquals(3, page.getTotalElements());
    }
}
