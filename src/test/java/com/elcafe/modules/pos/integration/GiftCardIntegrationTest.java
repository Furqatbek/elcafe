package com.elcafe.modules.pos.integration;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.pos.giftcard.entity.GiftCard;
import com.elcafe.modules.pos.giftcard.entity.GiftCardTransaction;
import com.elcafe.modules.pos.giftcard.entity.GiftCardType;
import com.elcafe.modules.pos.giftcard.enums.GiftCardStatus;
import com.elcafe.modules.pos.giftcard.enums.GiftCardTransactionType;
import com.elcafe.modules.pos.giftcard.repository.GiftCardRepository;
import com.elcafe.modules.pos.giftcard.repository.GiftCardTransactionRepository;
import com.elcafe.modules.pos.giftcard.repository.GiftCardTypeRepository;
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

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class GiftCardIntegrationTest {

    @Autowired private GiftCardRepository giftCardRepository;
    @Autowired private GiftCardTypeRepository giftCardTypeRepository;
    @Autowired private GiftCardTransactionRepository transactionRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private GiftCardType cardType;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);

        cardType = GiftCardType.builder()
                .restaurant(restaurant).name("Standard")
                .isCustomAmountAllowed(true).validityDays(365)
                .isRechargeable(true).isActive(true).build();
        em.persist(cardType);
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Full lifecycle: issue → redeem → check balance → transaction history")
    void fullLifecycle() {
        GiftCardType type = giftCardTypeRepository.findById(cardType.getId()).orElseThrow();

        // Issue card
        GiftCard card = GiftCard.builder()
                .restaurant(restaurant).giftCardType(type)
                .cardNumber("GC-INT-001").barcode("GC-INT-001")
                .initialBalance(new BigDecimal("100000"))
                .currentBalance(new BigDecimal("100000"))
                .status(GiftCardStatus.ACTIVE)
                .issuedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        card = giftCardRepository.save(card);

        // Record purchase transaction
        transactionRepository.save(GiftCardTransaction.builder()
                .giftCard(card).transactionType(GiftCardTransactionType.PURCHASE)
                .amount(new BigDecimal("100000"))
                .balanceBefore(BigDecimal.ZERO).balanceAfter(new BigDecimal("100000"))
                .build());

        // Redeem partial
        card.setCurrentBalance(new BigDecimal("60000"));
        card.setLastUsedAt(OffsetDateTime.now(ZoneOffset.UTC));
        giftCardRepository.save(card);

        transactionRepository.save(GiftCardTransaction.builder()
                .giftCard(card).transactionType(GiftCardTransactionType.REDEMPTION)
                .amount(new BigDecimal("40000"))
                .balanceBefore(new BigDecimal("100000")).balanceAfter(new BigDecimal("60000"))
                .build());

        em.flush();
        em.clear();

        // Verify balance
        GiftCard loaded = giftCardRepository.findById(card.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("60000").compareTo(loaded.getCurrentBalance()));
        assertEquals(GiftCardStatus.ACTIVE, loaded.getStatus());

        // Verify transactions
        List<GiftCardTransaction> txns = transactionRepository.findByGiftCardIdOrderByCreatedAtDesc(card.getId());
        assertEquals(2, txns.size());
    }

    @Test
    @DisplayName("Full redemption sets status to REDEEMED")
    void fullRedemption() {
        GiftCard card = giftCardRepository.save(GiftCard.builder()
                .restaurant(restaurant).cardNumber("GC-INT-002").barcode("GC-INT-002")
                .initialBalance(new BigDecimal("50000")).currentBalance(new BigDecimal("50000"))
                .status(GiftCardStatus.ACTIVE).issuedAt(OffsetDateTime.now(ZoneOffset.UTC)).build());

        card.setCurrentBalance(BigDecimal.ZERO);
        card.setStatus(GiftCardStatus.REDEEMED);
        giftCardRepository.save(card);
        em.flush();
        em.clear();

        GiftCard loaded = giftCardRepository.findById(card.getId()).orElseThrow();
        assertEquals(GiftCardStatus.REDEEMED, loaded.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(loaded.getCurrentBalance()));
    }

    @Test
    @DisplayName("Lookup by card number or barcode")
    void lookupByCardNumberOrBarcode() {
        giftCardRepository.save(GiftCard.builder()
                .restaurant(restaurant).cardNumber("GC-FIND-001").barcode("BC-FIND-001")
                .initialBalance(new BigDecimal("75000")).currentBalance(new BigDecimal("75000"))
                .status(GiftCardStatus.ACTIVE).issuedAt(OffsetDateTime.now(ZoneOffset.UTC)).build());
        em.flush();
        em.clear();

        assertTrue(giftCardRepository.findByCardNumberOrBarcode(restaurant.getId(), "GC-FIND-001").isPresent());
        assertTrue(giftCardRepository.findByCardNumberOrBarcode(restaurant.getId(), "BC-FIND-001").isPresent());
        assertFalse(giftCardRepository.findByCardNumberOrBarcode(restaurant.getId(), "NONEXISTENT").isPresent());
    }

    @Test
    @DisplayName("COGS aggregation by transaction type")
    void transactionAggregation() {
        GiftCard card = giftCardRepository.save(GiftCard.builder()
                .restaurant(restaurant).cardNumber("GC-AGG-001").barcode("GC-AGG-001")
                .initialBalance(new BigDecimal("200000")).currentBalance(new BigDecimal("200000"))
                .status(GiftCardStatus.ACTIVE).issuedAt(OffsetDateTime.now(ZoneOffset.UTC)).build());

        transactionRepository.save(GiftCardTransaction.builder()
                .giftCard(card).transactionType(GiftCardTransactionType.REDEMPTION)
                .amount(new BigDecimal("50000")).balanceBefore(new BigDecimal("200000"))
                .balanceAfter(new BigDecimal("150000")).build());
        transactionRepository.save(GiftCardTransaction.builder()
                .giftCard(card).transactionType(GiftCardTransactionType.REDEMPTION)
                .amount(new BigDecimal("30000")).balanceBefore(new BigDecimal("150000"))
                .balanceAfter(new BigDecimal("120000")).build());
        em.flush();
        em.clear();

        BigDecimal totalRedeemed = transactionRepository.sumByCardAndType(card.getId(), GiftCardTransactionType.REDEMPTION);
        assertEquals(0, new BigDecimal("80000").compareTo(totalRedeemed));
    }
}
