package com.elcafe.modules.promotion.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.promotion.entity.Promotion;
import com.elcafe.modules.promotion.enums.PromotionScope;
import com.elcafe.modules.promotion.enums.PromotionType;
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

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class PromotionRepositoryTest {
    @Autowired private PromotionRepository promotionRepository;
    @Autowired private EntityManager em;
    private Restaurant restaurant;

    @BeforeEach void setUp() {
        restaurant = new Restaurant(); restaurant.setName("Test"); restaurant.setAddress("123"); restaurant.setActive(true);
        em.persist(restaurant);
        em.persist(Promotion.builder().restaurant(restaurant).name("Active Sale")
                .promotionType(PromotionType.PERCENTAGE).promotionScope(PromotionScope.ALL)
                .discountValue(new BigDecimal("20")).active(true)
                .startDate(LocalDateTime.now().minusDays(5)).endDate(LocalDateTime.now().plusDays(5)).build());
        em.persist(Promotion.builder().restaurant(restaurant).name("Expired Sale")
                .promotionType(PromotionType.PERCENTAGE).promotionScope(PromotionScope.ALL)
                .discountValue(new BigDecimal("10")).active(true)
                .startDate(LocalDateTime.now().minusDays(30)).endDate(LocalDateTime.now().minusDays(1)).build());
        em.persist(Promotion.builder().restaurant(restaurant).name("Inactive")
                .promotionType(PromotionType.FIXED_AMOUNT).promotionScope(PromotionScope.ALL)
                .discountValue(new BigDecimal("5000")).active(false)
                .startDate(LocalDateTime.now().minusDays(1)).endDate(LocalDateTime.now().plusDays(30)).build());
        em.flush(); em.clear();
    }

    @Test @DisplayName("findActivePromotions — filters by active + date range")
    void activeByDateRange() {
        List<Promotion> active = promotionRepository.findActivePromotions(restaurant.getId(), LocalDateTime.now());
        assertEquals(1, active.size());
        assertEquals("Active Sale", active.get(0).getName());
    }

    @Test @DisplayName("existsByRestaurant_IdAndNameIgnoreCase — case-insensitive duplicate check")
    void duplicateNameCheck() {
        assertTrue(promotionRepository.existsByRestaurant_IdAndNameIgnoreCase(restaurant.getId(), "active sale"));
        assertFalse(promotionRepository.existsByRestaurant_IdAndNameIgnoreCase(restaurant.getId(), "nonexistent"));
    }

    @Test @DisplayName("findByIdWithDetails — eager loads rule and products")
    void findWithDetails() {
        Promotion p = promotionRepository.findAll().get(0);
        Promotion loaded = promotionRepository.findByIdWithDetails(p.getId());
        assertNotNull(loaded);
        assertEquals(p.getName(), loaded.getName());
    }
}
