package com.elcafe.modules.pos.giftcard.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.pos.giftcard.entity.GiftCard;
import com.elcafe.modules.pos.giftcard.enums.GiftCardStatus;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class GiftCardRepositoryTest {
    @Autowired private GiftCardRepository giftCardRepository;
    @Autowired private EntityManager em;
    private Restaurant restaurant;

    @BeforeEach void setUp() {
        restaurant = new Restaurant(); restaurant.setName("Test"); restaurant.setAddress("123"); restaurant.setActive(true);
        em.persist(restaurant);
        em.persist(GiftCard.builder().restaurant(restaurant).cardNumber("GC-001").barcode("BC-001")
                .initialBalance(new BigDecimal("100000")).currentBalance(new BigDecimal("100000"))
                .status(GiftCardStatus.ACTIVE).issuedAt(OffsetDateTime.now(ZoneOffset.UTC)).build());
        em.persist(GiftCard.builder().restaurant(restaurant).cardNumber("GC-002").barcode("BC-002")
                .initialBalance(new BigDecimal("50000")).currentBalance(BigDecimal.ZERO)
                .status(GiftCardStatus.REDEEMED).issuedAt(OffsetDateTime.now(ZoneOffset.UTC)).build());
        em.flush(); em.clear();
    }

    @Test @DisplayName("findByCardNumberOrBarcode — finds by card number or barcode")
    void byCardNumberOrBarcode() {
        assertTrue(giftCardRepository.findByCardNumberOrBarcode(restaurant.getId(), "GC-001").isPresent());
        assertTrue(giftCardRepository.findByCardNumberOrBarcode(restaurant.getId(), "BC-001").isPresent());
        assertFalse(giftCardRepository.findByCardNumberOrBarcode(restaurant.getId(), "NONE").isPresent());
    }

    @Test @DisplayName("findByRestaurantIdAndStatus — filters by status")
    void byStatus() {
        List<GiftCard> active = giftCardRepository.findByRestaurantIdAndStatus(restaurant.getId(), GiftCardStatus.ACTIVE);
        assertEquals(1, active.size());
        assertEquals("GC-001", active.get(0).getCardNumber());
    }

    @Test @DisplayName("existsByRestaurantIdAndCardNumber — checks uniqueness")
    void existsByCardNumber() {
        assertTrue(giftCardRepository.existsByRestaurantIdAndCardNumber(restaurant.getId(), "GC-001"));
        assertFalse(giftCardRepository.existsByRestaurantIdAndCardNumber(restaurant.getId(), "GC-999"));
    }
}
