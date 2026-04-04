package com.elcafe.modules.promotion.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.promotion.entity.CouponCode;
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

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class CouponCodeRepositoryTest {
    @Autowired private CouponCodeRepository couponCodeRepository;
    @Autowired private EntityManager em;
    private Promotion promotion;

    @BeforeEach void setUp() {
        Restaurant restaurant = new Restaurant(); restaurant.setName("Test"); restaurant.setAddress("123"); restaurant.setActive(true);
        em.persist(restaurant);
        promotion = Promotion.builder().restaurant(restaurant).name("Sale")
                .promotionType(PromotionType.PERCENTAGE).promotionScope(PromotionScope.ALL)
                .discountValue(new BigDecimal("20")).active(true).build();
        em.persist(promotion);
        em.persist(CouponCode.builder().code("ACTIVE10").promotion(promotion).active(true)
                .usedCount(0).validFrom(LocalDateTime.now().minusDays(1)).validUntil(LocalDateTime.now().plusDays(30)).build());
        em.persist(CouponCode.builder().code("EXPIRED10").promotion(promotion).active(true)
                .usedCount(0).validFrom(LocalDateTime.now().minusDays(30)).validUntil(LocalDateTime.now().minusDays(1)).build());
        em.persist(CouponCode.builder().code("INACTIVE10").promotion(promotion).active(false).usedCount(0).build());
        em.flush(); em.clear();
    }

    @Test @DisplayName("findByCodeIgnoreCase — case-insensitive lookup")
    void findByCode() {
        assertTrue(couponCodeRepository.findByCodeIgnoreCase("active10").isPresent());
        assertTrue(couponCodeRepository.findByCodeIgnoreCase("ACTIVE10").isPresent());
        assertFalse(couponCodeRepository.findByCodeIgnoreCase("NONEXISTENT").isPresent());
    }

    @Test @DisplayName("existsByCodeIgnoreCase — uniqueness check")
    void existsByCode() {
        assertTrue(couponCodeRepository.existsByCodeIgnoreCase("active10"));
        assertFalse(couponCodeRepository.existsByCodeIgnoreCase("NEW_CODE"));
    }

    @Test @DisplayName("countByPromotionId — counts coupons for promotion")
    void countByPromotion() {
        Long count = couponCodeRepository.countByPromotionId(promotion.getId());
        assertEquals(3, count);
    }
}
