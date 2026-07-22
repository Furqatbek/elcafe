package com.elcafe.modules.restaurant.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.entity.RestaurantTable.TableStatus;
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
class RestaurantTableRepositoryTest {

    @Autowired private RestaurantTableRepository restaurantTableRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);
    }

    private RestaurantTable createTable(String number, TableStatus status, String section) {
        RestaurantTable table = RestaurantTable.builder()
                .restaurant(restaurant)
                .tableNumber(number)
                .status(status)
                .capacity(4)
                .active(true)
                .section(section)
                .build();
        em.persist(table);
        return table;
    }

    @Test
    @DisplayName("findDistinctSectionsByRestaurantId returns unique non-null sections")
    void findDistinctSectionsByRestaurantId() {
        createTable("T1", TableStatus.AVAILABLE, "Patio");
        createTable("T2", TableStatus.OCCUPIED, "Patio");
        createTable("T3", TableStatus.AVAILABLE, "Indoor");
        createTable("T4", TableStatus.AVAILABLE, null);

        em.flush();
        em.clear();

        List<String> sections = restaurantTableRepository.findDistinctSectionsByRestaurantId(
                restaurant.getId());

        assertEquals(2, sections.size());
        assertTrue(sections.contains("Patio"));
        assertTrue(sections.contains("Indoor"));
    }

    @Test
    @DisplayName("countByRestaurantIdAndStatus counts tables with specific status")
    void countByRestaurantIdAndStatus() {
        createTable("T1", TableStatus.AVAILABLE, "Main");
        createTable("T2", TableStatus.AVAILABLE, "Main");
        createTable("T3", TableStatus.OCCUPIED, "Main");
        createTable("T4", TableStatus.CLEANING, "Patio");

        em.flush();
        em.clear();

        Long available = restaurantTableRepository.countByRestaurantIdAndStatus(
                restaurant.getId(), TableStatus.AVAILABLE);
        Long occupied = restaurantTableRepository.countByRestaurantIdAndStatus(
                restaurant.getId(), TableStatus.OCCUPIED);
        Long reserved = restaurantTableRepository.countByRestaurantIdAndStatus(
                restaurant.getId(), TableStatus.RESERVED);

        assertEquals(2L, available);
        assertEquals(1L, occupied);
        assertEquals(0L, reserved);
    }
}
