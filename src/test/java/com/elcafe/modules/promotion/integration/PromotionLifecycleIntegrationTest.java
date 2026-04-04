package com.elcafe.modules.promotion.integration;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.promotion.entity.*;
import com.elcafe.modules.promotion.enums.PromotionScope;
import com.elcafe.modules.promotion.enums.PromotionType;
import com.elcafe.modules.promotion.repository.PromotionRepository;
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
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class PromotionLifecycleIntegrationTest {

    @Autowired private PromotionRepository promotionRepository;
    @Autowired private EntityManager em;
    private Restaurant restaurant;

    @BeforeEach void setUp() {
        restaurant = new Restaurant(); restaurant.setName("Test"); restaurant.setAddress("123"); restaurant.setActive(true);
        em.persist(restaurant); em.flush(); em.clear();
    }

    @Test @DisplayName("Create promotion with rule and products")
    void createWithRuleAndProducts() {
        Promotion promo = Promotion.builder().restaurant(restaurant).name("Summer Sale")
                .promotionType(PromotionType.PERCENTAGE).promotionScope(PromotionScope.ALL)
                .discountValue(new BigDecimal("20")).active(true)
                .startDate(LocalDateTime.now().minusDays(1)).endDate(LocalDateTime.now().plusDays(30))
                .build();
        PromotionRule rule = PromotionRule.builder().promotion(promo)
                .minOrderAmount(new BigDecimal("50000")).build();
        promo.setRule(rule);
        promo.setPromotionProducts(new ArrayList<>());
        promo = promotionRepository.save(promo);
        em.flush(); em.clear();

        Promotion loaded = promotionRepository.findByIdWithDetails(promo.getId());
        assertNotNull(loaded);
        assertEquals("Summer Sale", loaded.getName());
        assertNotNull(loaded.getRule());
    }

    @Test @DisplayName("Active promotions filter by date range")
    void activeFilter() {
        promotionRepository.save(Promotion.builder().restaurant(restaurant).name("Active")
                .promotionType(PromotionType.PERCENTAGE).promotionScope(PromotionScope.ALL)
                .discountValue(new BigDecimal("10")).active(true)
                .startDate(LocalDateTime.now().minusDays(5)).endDate(LocalDateTime.now().plusDays(5))
                .build());
        promotionRepository.save(Promotion.builder().restaurant(restaurant).name("Expired")
                .promotionType(PromotionType.PERCENTAGE).promotionScope(PromotionScope.ALL)
                .discountValue(new BigDecimal("10")).active(true)
                .startDate(LocalDateTime.now().minusDays(30)).endDate(LocalDateTime.now().minusDays(1))
                .build());
        em.flush(); em.clear();

        List<Promotion> active = promotionRepository.findActivePromotions(restaurant.getId(), LocalDateTime.now());
        assertEquals(1, active.size());
        assertEquals("Active", active.get(0).getName());
    }

    @Test @DisplayName("Duplicate name validation")
    void duplicateName() {
        promotionRepository.save(Promotion.builder().restaurant(restaurant).name("Unique")
                .promotionType(PromotionType.PERCENTAGE).promotionScope(PromotionScope.ALL)
                .discountValue(new BigDecimal("10")).active(true).build());
        em.flush(); em.clear();

        assertTrue(promotionRepository.existsByRestaurant_IdAndNameIgnoreCase(restaurant.getId(), "unique"));
        assertFalse(promotionRepository.existsByRestaurant_IdAndNameIgnoreCase(restaurant.getId(), "other"));
    }

    @Test @DisplayName("Promotion isValid checks active + date range")
    void isValidCheck() {
        Promotion valid = Promotion.builder().restaurant(restaurant).name("Valid")
                .promotionType(PromotionType.PERCENTAGE).promotionScope(PromotionScope.ALL)
                .discountValue(new BigDecimal("10")).active(true)
                .startDate(LocalDateTime.now().minusDays(1)).endDate(LocalDateTime.now().plusDays(1))
                .build();
        valid = promotionRepository.save(valid);
        em.flush(); em.clear();

        Promotion loaded = promotionRepository.findById(valid.getId()).orElseThrow();
        assertTrue(loaded.isValid());
    }
}
