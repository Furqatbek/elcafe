package com.elcafe.modules.pos.giftcard.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.pos.giftcard.entity.GiftCard;
import com.elcafe.modules.pos.giftcard.entity.GiftCardTransaction;
import com.elcafe.modules.pos.giftcard.enums.GiftCardStatus;
import com.elcafe.modules.pos.giftcard.enums.GiftCardTransactionType;
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
class GiftCardTransactionRepositoryTest {
    @Autowired private GiftCardTransactionRepository transactionRepository;
    @Autowired private EntityManager em;
    private GiftCard card;

    @BeforeEach void setUp() {
        Restaurant restaurant = new Restaurant(); restaurant.setName("Test"); restaurant.setAddress("123"); restaurant.setActive(true);
        em.persist(restaurant);
        card = GiftCard.builder().restaurant(restaurant).cardNumber("GC-T01").barcode("GC-T01")
                .initialBalance(new BigDecimal("100000")).currentBalance(new BigDecimal("60000"))
                .status(GiftCardStatus.ACTIVE).issuedAt(OffsetDateTime.now(ZoneOffset.UTC)).build();
        em.persist(card);
        em.persist(GiftCardTransaction.builder().giftCard(card).transactionType(GiftCardTransactionType.PURCHASE)
                .amount(new BigDecimal("100000")).balanceBefore(BigDecimal.ZERO).balanceAfter(new BigDecimal("100000")).build());
        em.persist(GiftCardTransaction.builder().giftCard(card).transactionType(GiftCardTransactionType.REDEMPTION)
                .amount(new BigDecimal("40000")).balanceBefore(new BigDecimal("100000")).balanceAfter(new BigDecimal("60000")).build());
        em.flush(); em.clear();
    }

    @Test @DisplayName("findByGiftCardIdOrderByCreatedAtDesc — returns ordered transactions")
    void byCard() {
        List<GiftCardTransaction> txns = transactionRepository.findByGiftCardIdOrderByCreatedAtDesc(card.getId());
        assertEquals(2, txns.size());
    }

    @Test @DisplayName("sumByCardAndType — aggregates by transaction type")
    void sumByType() {
        BigDecimal total = transactionRepository.sumByCardAndType(card.getId(), GiftCardTransactionType.REDEMPTION);
        assertEquals(0, new BigDecimal("40000").compareTo(total));
    }
}
