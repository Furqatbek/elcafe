package com.elcafe.modules.pos.giftcard.service;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.exception.BadRequestException;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.pos.giftcard.dto.*;
import com.elcafe.modules.pos.giftcard.entity.GiftCard;
import com.elcafe.modules.pos.giftcard.entity.GiftCardTransaction;
import com.elcafe.modules.pos.giftcard.entity.GiftCardType;
import com.elcafe.modules.pos.giftcard.enums.GiftCardStatus;
import com.elcafe.modules.pos.giftcard.enums.GiftCardTransactionType;
import com.elcafe.modules.pos.giftcard.repository.GiftCardRepository;
import com.elcafe.modules.pos.giftcard.repository.GiftCardTransactionRepository;
import com.elcafe.modules.pos.giftcard.repository.GiftCardTypeRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GiftCardServiceTest {

    @Mock private GiftCardRepository giftCardRepository;
    @Mock private GiftCardTypeRepository giftCardTypeRepository;
    @Mock private GiftCardTransactionRepository transactionRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private GiftCardService giftCardService;

    private Restaurant restaurant;
    private GiftCardType cardType;
    private GiftCard giftCard;
    private User operator;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test");

        cardType = GiftCardType.builder()
                .id(1L).restaurant(restaurant).name("Standard")
                .isCustomAmountAllowed(true).minAmount(new BigDecimal("10000"))
                .maxAmount(new BigDecimal("500000")).validityDays(365)
                .isRechargeable(true).isActive(true).build();

        giftCard = GiftCard.builder()
                .id(1L).restaurant(restaurant).giftCardType(cardType)
                .cardNumber("GC-TEST-001").barcode("GC-TEST-001")
                .initialBalance(new BigDecimal("100000"))
                .currentBalance(new BigDecimal("100000"))
                .status(GiftCardStatus.ACTIVE)
                .issuedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(365))
                .build();

        operator = new User();
        operator.setId(1L);
        operator.setEmail("admin@test.com");
    }

    @Test @DisplayName("createGiftCardType — success")
    void createGiftCardType_success() {
        CreateGiftCardTypeRequest request = new CreateGiftCardTypeRequest();
        request.setName("Premium");
        request.setIsCustomAmountAllowed(true);
        request.setValidityDays(365);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(giftCardTypeRepository.existsByRestaurantIdAndName(1L, "Premium")).thenReturn(false);
        when(giftCardTypeRepository.save(any())).thenAnswer(i -> { GiftCardType t = i.getArgument(0); t.setId(2L); return t; });

        GiftCardType result = giftCardService.createGiftCardType(1L, request);

        assertThat(result.getName()).isEqualTo("Premium");
    }

    @Test @DisplayName("getGiftCardTypes — returns active list")
    void getGiftCardTypes_returnsList() {
        when(giftCardTypeRepository.findByRestaurantIdAndIsActiveTrue(1L)).thenReturn(List.of(cardType));
        assertThat(giftCardService.getGiftCardTypes(1L)).hasSize(1);
    }

    @Test @DisplayName("issueGiftCard — success")
    void issueGiftCard_success() {
        IssueGiftCardRequest request = new IssueGiftCardRequest();
        request.setGiftCardTypeId(1L);
        request.setAmount(new BigDecimal("50000"));
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));
        when(giftCardTypeRepository.findByIdAndRestaurantId(1L, 1L)).thenReturn(Optional.of(cardType));
        when(giftCardRepository.existsByRestaurantIdAndCardNumber(anyLong(), anyString())).thenReturn(false);
        when(giftCardRepository.save(any())).thenAnswer(i -> { GiftCard g = i.getArgument(0); g.setId(2L); return g; });
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        GiftCard result = giftCardService.issueGiftCard(1L, request, 1L);

        assertThat(result.getInitialBalance()).isEqualByComparingTo("50000");
        verify(transactionRepository).save(any(GiftCardTransaction.class));
    }

    @Test @DisplayName("issueGiftCard — invalid type throws")
    void issueGiftCard_invalidType_throws() {
        IssueGiftCardRequest request = new IssueGiftCardRequest();
        request.setGiftCardTypeId(99L);
        request.setAmount(new BigDecimal("50000"));
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));
        when(giftCardTypeRepository.findByIdAndRestaurantId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> giftCardService.issueGiftCard(1L, request, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Gift card type not found");
    }

    @Test @DisplayName("checkBalance — found")
    void getGiftCardBalance_found() {
        when(giftCardRepository.findByCardNumberOrBarcode(1L, "GC-TEST-001")).thenReturn(Optional.of(giftCard));

        GiftCardBalanceResponse result = giftCardService.checkBalance(1L, "GC-TEST-001");

        assertThat(result.getCurrentBalance()).isEqualByComparingTo("100000");
        assertThat(result.getCardNumber()).isEqualTo("GC-TEST-001");
    }

    @Test @DisplayName("checkBalance — not found throws")
    void getGiftCardBalance_notFound_throws() {
        when(giftCardRepository.findByCardNumberOrBarcode(1L, "INVALID")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> giftCardService.checkBalance(1L, "INVALID"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Gift card not found");
    }

    @Test @DisplayName("redeemGiftCard — success")
    void redeemGiftCard_success() {
        RedeemGiftCardRequest request = new RedeemGiftCardRequest();
        request.setCardNumberOrBarcode("GC-TEST-001");
        request.setAmount(new BigDecimal("30000"));
        when(giftCardRepository.findByCardNumberOrBarcode(1L, "GC-TEST-001")).thenReturn(Optional.of(giftCard));
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));
        when(giftCardRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RedeemResult result = giftCardService.redeemGiftCard(1L, request, null, null, 1L);

        assertThat(result.getAmountRedeemed()).isEqualByComparingTo("30000");
        assertThat(result.getRemainingBalance()).isEqualByComparingTo("70000");
    }

    @Test @DisplayName("redeemGiftCard — insufficient uses partial balance")
    void redeemGiftCard_insufficientBalance_usesPartial() {
        giftCard.setCurrentBalance(new BigDecimal("20000"));
        RedeemGiftCardRequest request = new RedeemGiftCardRequest();
        request.setCardNumberOrBarcode("GC-TEST-001");
        request.setAmount(new BigDecimal("50000"));
        when(giftCardRepository.findByCardNumberOrBarcode(1L, "GC-TEST-001")).thenReturn(Optional.of(giftCard));
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));
        when(giftCardRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        RedeemResult result = giftCardService.redeemGiftCard(1L, request, null, null, 1L);

        assertThat(result.getAmountRedeemed()).isEqualByComparingTo("20000");
        assertThat(result.getRemainingBalance()).isEqualByComparingTo("0");
    }

    @Test @DisplayName("redeemGiftCard — expired card throws")
    void redeemGiftCard_expired_throws() {
        giftCard.setStatus(GiftCardStatus.EXPIRED);
        RedeemGiftCardRequest request = new RedeemGiftCardRequest();
        request.setCardNumberOrBarcode("GC-TEST-001");
        request.setAmount(new BigDecimal("10000"));
        when(giftCardRepository.findByCardNumberOrBarcode(1L, "GC-TEST-001")).thenReturn(Optional.of(giftCard));
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));

        assertThatThrownBy(() -> giftCardService.redeemGiftCard(1L, request, null, null, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not valid");
    }

    @Test @DisplayName("getTransactionHistory — returns paginated")
    void getGiftCardTransactions_returnsList() {
        GiftCardTransaction txn = GiftCardTransaction.builder().id(1L).giftCard(giftCard)
                .transactionType(GiftCardTransactionType.PURCHASE).amount(new BigDecimal("100000")).build();
        when(giftCardRepository.findByCardNumberOrBarcode(1L, "GC-TEST-001")).thenReturn(Optional.of(giftCard));
        when(transactionRepository.findByGiftCardIdOrderByCreatedAtDesc(1L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(txn), PageRequest.of(0, 20), 1));

        var result = giftCardService.getTransactionHistory(1L, "GC-TEST-001", PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test @DisplayName("reloadGiftCard — adds balance")
    void reloadGiftCard_success() {
        when(giftCardRepository.findByCardNumberOrBarcode(1L, "GC-TEST-001")).thenReturn(Optional.of(giftCard));
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));
        when(giftCardRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        GiftCard result = giftCardService.reloadGiftCard(1L, "GC-TEST-001", new BigDecimal("50000"), 1L);

        assertThat(result.getCurrentBalance()).isEqualByComparingTo("150000");
        verify(transactionRepository).save(any(GiftCardTransaction.class));
    }

    @Test @DisplayName("refundToGiftCard — adds refund amount")
    void refundToGiftCard_success() {
        when(giftCardRepository.findByCardNumberOrBarcode(1L, "GC-TEST-001")).thenReturn(Optional.of(giftCard));
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));
        when(giftCardRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        GiftCard result = giftCardService.refundToGiftCard(1L, "GC-TEST-001", new BigDecimal("30000"), null, 1L, "Order refund");

        assertThat(result.getCurrentBalance()).isEqualByComparingTo("130000");
        verify(transactionRepository).save(any(GiftCardTransaction.class));
    }

    @Test @DisplayName("listGiftCards — returns paginated")
    void listGiftCards_returnsPaginated() {
        when(giftCardRepository.findByRestaurantIdOrderByCreatedAtDesc(1L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(giftCard), PageRequest.of(0, 20), 1));

        var result = giftCardService.listGiftCards(1L, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getCardNumber()).isEqualTo("GC-TEST-001");
    }

    @Test @DisplayName("expireGiftCards — marks expired cards")
    void expireGiftCards_marksExpired() {
        GiftCard expiredCard = GiftCard.builder()
                .id(2L).restaurant(restaurant).giftCardType(cardType)
                .cardNumber("GC-EXPIRED-001").barcode("GC-EXPIRED-001")
                .initialBalance(new BigDecimal("50000"))
                .currentBalance(new BigDecimal("25000"))
                .status(GiftCardStatus.ACTIVE)
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1))
                .build();
        when(giftCardRepository.findExpiredCards(any(OffsetDateTime.class))).thenReturn(List.of(expiredCard));
        when(giftCardRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        giftCardService.expireGiftCards();

        assertThat(expiredCard.getStatus()).isEqualTo(GiftCardStatus.EXPIRED);
        verify(giftCardRepository, atLeastOnce()).save(expiredCard);
        verify(transactionRepository).save(any(GiftCardTransaction.class));
    }
}
