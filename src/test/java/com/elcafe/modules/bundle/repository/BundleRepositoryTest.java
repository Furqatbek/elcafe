package com.elcafe.modules.bundle.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.bundle.entity.Bundle;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class BundleRepositoryTest {

    @Autowired private BundleRepository bundleRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = Restaurant.builder().name("Test Cafe").address("123 Main St").build();
        em.persist(restaurant);
    }

    @Test @DisplayName("findByRestaurantIdAndNameIgnoreCase — case-insensitive name lookup")
    void findByRestaurantIdAndNameIgnoreCase() {
        em.persist(Bundle.builder().restaurant(restaurant).name("Breakfast Combo")
                .bundlePrice(new BigDecimal("15.99")).active(true).displayOrder(0).build());

        em.flush(); em.clear();

        Optional<Bundle> found = bundleRepository.findByRestaurantIdAndNameIgnoreCase(
                restaurant.getId(), "breakfast combo");
        assertTrue(found.isPresent());
        assertEquals("Breakfast Combo", found.get().getName());

        Optional<Bundle> notFound = bundleRepository.findByRestaurantIdAndNameIgnoreCase(
                restaurant.getId(), "Lunch Combo");
        assertFalse(notFound.isPresent());
    }

    @Test @DisplayName("countActiveByRestaurantId — counts only active bundles")
    void countActiveByRestaurantId() {
        em.persist(Bundle.builder().restaurant(restaurant).name("Bundle A")
                .bundlePrice(new BigDecimal("10.00")).active(true).displayOrder(0).build());
        em.persist(Bundle.builder().restaurant(restaurant).name("Bundle B")
                .bundlePrice(new BigDecimal("20.00")).active(true).displayOrder(1).build());
        em.persist(Bundle.builder().restaurant(restaurant).name("Bundle C")
                .bundlePrice(new BigDecimal("30.00")).active(false).displayOrder(2).build());

        em.flush(); em.clear();

        assertEquals(2L, bundleRepository.countActiveByRestaurantId(restaurant.getId()));
    }
}
