package com.elcafe.modules.pos.giftcard.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.Payment;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing gift cards - purchase, redeem, balance check, etc.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GiftCardService {

    private final GiftCardRepository giftCardRepository;
    private final GiftCardTypeRepository giftCardTypeRepository;
    private final GiftCardTransactionRepository transactionRepository;
    private final RestaurantRepository restaurantRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    /**
     * Create a gift card type.
     */
    @Transactional
    public GiftCardType createGiftCardType(Long restaurantId, CreateGiftCardTypeRequest request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));

        if (giftCardTypeRepository.existsByRestaurantIdAndName(restaurantId, request.getName())) {
            throw new IllegalArgumentException("Gift card type with this name already exists");
        }

        GiftCardType type = GiftCardType.builder()
            .restaurant(restaurant)
            .name(request.getName())
            .description(request.getDescription())
            .fixedAmounts(request.getFixedAmounts())
            .minAmount(request.getMinAmount())
            .maxAmount(request.getMaxAmount())
            .isCustomAmountAllowed(request.getIsCustomAmountAllowed())
            .validityDays(request.getValidityDays())
            .isRechargeable(request.getIsRechargeable())
            .isActive(true)
            .build();

        return giftCardTypeRepository.save(type);
    }

    /**
     * Get active gift card types for a restaurant.
     */
    public List<GiftCardType> getGiftCardTypes(Long restaurantId) {
        return giftCardTypeRepository.findByRestaurantIdAndIsActiveTrue(restaurantId);
    }

    /**
     * Issue/purchase a new gift card.
     */
    @Transactional
    public GiftCard issueGiftCard(Long restaurantId, IssueGiftCardRequest request, Long operatorId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));

        User operator = userRepository.findById(operatorId)
            .orElseThrow(() -> new IllegalArgumentException("Operator not found"));

        // Validate amount
        GiftCardType type = null;
        if (request.getGiftCardTypeId() != null) {
            type = giftCardTypeRepository.findByIdAndRestaurantId(request.getGiftCardTypeId(), restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Gift card type not found"));

            if (!type.getIsCustomAmountAllowed()) {
                if (type.getFixedAmounts() == null || !type.getFixedAmounts().contains(request.getAmount())) {
                    throw new IllegalArgumentException("Amount must be one of the fixed amounts");
                }
            } else {
                if (type.getMinAmount() != null && request.getAmount().compareTo(type.getMinAmount()) < 0) {
                    throw new IllegalArgumentException("Amount below minimum");
                }
                if (type.getMaxAmount() != null && request.getAmount().compareTo(type.getMaxAmount()) > 0) {
                    throw new IllegalArgumentException("Amount above maximum");
                }
            }
        }

        // Generate card number
        String cardNumber = request.getCardNumber() != null ?
            request.getCardNumber() : generateCardNumber();

        if (giftCardRepository.existsByRestaurantIdAndCardNumber(restaurantId, cardNumber)) {
            throw new IllegalArgumentException("Card number already exists");
        }

        // Calculate expiration
        OffsetDateTime expiresAt = null;
        if (type != null && type.getValidityDays() != null) {
            expiresAt = OffsetDateTime.now().plusDays(type.getValidityDays());
        }

        // Get customer if specified
        Customer customer = null;
        if (request.getPurchasedByCustomerId() != null) {
            customer = customerRepository.findById(request.getPurchasedByCustomerId()).orElse(null);
        }

        GiftCard giftCard = GiftCard.builder()
            .restaurant(restaurant)
            .giftCardType(type)
            .cardNumber(cardNumber)
            .pin(request.getPin())
            .barcode(request.getBarcode() != null ? request.getBarcode() : cardNumber)
            .initialBalance(request.getAmount())
            .currentBalance(request.getAmount())
            .status(GiftCardStatus.ACTIVE)
            .purchasedAt(OffsetDateTime.now())
            .purchasedByCustomer(customer)
            .purchaseAmount(request.getAmount())
            .recipientName(request.getRecipientName())
            .recipientEmail(request.getRecipientEmail())
            .recipientPhone(request.getRecipientPhone())
            .personalMessage(request.getPersonalMessage())
            .issuedAt(OffsetDateTime.now())
            .expiresAt(expiresAt)
            .build();

        giftCard = giftCardRepository.save(giftCard);

        // Record transaction
        GiftCardTransaction transaction = GiftCardTransaction.builder()
            .giftCard(giftCard)
            .transactionType(GiftCardTransactionType.PURCHASE)
            .amount(request.getAmount())
            .balanceBefore(BigDecimal.ZERO)
            .balanceAfter(request.getAmount())
            .performedBy(operator)
            .notes("Gift card issued")
            .build();
        transactionRepository.save(transaction);

        log.info("Gift card {} issued with balance {}", cardNumber, request.getAmount());
        return giftCard;
    }

    /**
     * Check gift card balance.
     */
    public GiftCardBalanceResponse checkBalance(Long restaurantId, String cardNumberOrBarcode) {
        GiftCard card = findCard(restaurantId, cardNumberOrBarcode);

        return GiftCardBalanceResponse.builder()
            .cardNumber(card.getCardNumber())
            .currentBalance(card.getCurrentBalance())
            .status(card.getStatus())
            .expiresAt(card.getExpiresAt())
            .isValid(card.isValid())
            .build();
    }

    /**
     * Redeem gift card for payment.
     */
    @Transactional
    public RedeemResult redeemGiftCard(Long restaurantId, RedeemGiftCardRequest request,
                                        Order order, Payment payment, Long operatorId) {
        GiftCard card = findCard(restaurantId, request.getCardNumberOrBarcode());
        User operator = userRepository.findById(operatorId).orElse(null);

        // Validate PIN if required
        if (card.getPin() != null && !card.getPin().equals(request.getPin())) {
            throw new IllegalArgumentException("Invalid PIN");
        }

        if (!card.isValid()) {
            throw new IllegalArgumentException("Gift card is not valid for redemption");
        }

        BigDecimal amountToRedeem = request.getAmount();
        if (amountToRedeem.compareTo(card.getCurrentBalance()) > 0) {
            // Partial redemption - use all available balance
            amountToRedeem = card.getCurrentBalance();
        }

        BigDecimal balanceBefore = card.getCurrentBalance();
        BigDecimal balanceAfter = balanceBefore.subtract(amountToRedeem);

        // Update card balance
        card.setCurrentBalance(balanceAfter);
        card.setLastUsedAt(OffsetDateTime.now());
        if (balanceAfter.compareTo(BigDecimal.ZERO) == 0) {
            card.setStatus(GiftCardStatus.REDEEMED);
        }
        giftCardRepository.save(card);

        // Record transaction
        GiftCardTransaction transaction = GiftCardTransaction.builder()
            .giftCard(card)
            .order(order)
            .payment(payment)
            .transactionType(GiftCardTransactionType.REDEMPTION)
            .amount(amountToRedeem)
            .balanceBefore(balanceBefore)
            .balanceAfter(balanceAfter)
            .performedBy(operator)
            .build();
        transactionRepository.save(transaction);

        log.info("Gift card {} redeemed {} for order {}", card.getCardNumber(), amountToRedeem,
            order != null ? order.getId() : "N/A");

        return RedeemResult.builder()
            .amountRedeemed(amountToRedeem)
            .remainingBalance(balanceAfter)
            .cardNumber(card.getCardNumber())
            .build();
    }

    /**
     * Reload/recharge a gift card.
     */
    @Transactional
    public GiftCard reloadGiftCard(Long restaurantId, String cardNumberOrBarcode,
                                    BigDecimal amount, Long operatorId) {
        GiftCard card = findCard(restaurantId, cardNumberOrBarcode);
        User operator = userRepository.findById(operatorId).orElse(null);

        if (card.getGiftCardType() != null && !card.getGiftCardType().getIsRechargeable()) {
            throw new IllegalArgumentException("This gift card type is not rechargeable");
        }

        if (card.getStatus() == GiftCardStatus.EXPIRED || card.getStatus() == GiftCardStatus.CANCELLED) {
            throw new IllegalArgumentException("Cannot reload expired or cancelled gift card");
        }

        BigDecimal balanceBefore = card.getCurrentBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);

        card.setCurrentBalance(balanceAfter);
        if (card.getStatus() == GiftCardStatus.REDEEMED) {
            card.setStatus(GiftCardStatus.ACTIVE);
        }
        giftCardRepository.save(card);

        // Record transaction
        GiftCardTransaction transaction = GiftCardTransaction.builder()
            .giftCard(card)
            .transactionType(GiftCardTransactionType.RELOAD)
            .amount(amount)
            .balanceBefore(balanceBefore)
            .balanceAfter(balanceAfter)
            .performedBy(operator)
            .build();
        transactionRepository.save(transaction);

        log.info("Gift card {} reloaded with {}", card.getCardNumber(), amount);
        return card;
    }

    /**
     * Refund amount back to gift card.
     */
    @Transactional
    public GiftCard refundToGiftCard(Long restaurantId, String cardNumberOrBarcode,
                                      BigDecimal amount, Order order, Long operatorId, String notes) {
        GiftCard card = findCard(restaurantId, cardNumberOrBarcode);
        User operator = userRepository.findById(operatorId).orElse(null);

        BigDecimal balanceBefore = card.getCurrentBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);

        card.setCurrentBalance(balanceAfter);
        if (card.getStatus() == GiftCardStatus.REDEEMED) {
            card.setStatus(GiftCardStatus.ACTIVE);
        }
        giftCardRepository.save(card);

        // Record transaction
        GiftCardTransaction transaction = GiftCardTransaction.builder()
            .giftCard(card)
            .order(order)
            .transactionType(GiftCardTransactionType.REFUND)
            .amount(amount)
            .balanceBefore(balanceBefore)
            .balanceAfter(balanceAfter)
            .performedBy(operator)
            .notes(notes)
            .build();
        transactionRepository.save(transaction);

        log.info("Gift card {} refunded {}", card.getCardNumber(), amount);
        return card;
    }

    /**
     * Get transaction history for a gift card.
     */
    public Page<GiftCardTransaction> getTransactionHistory(Long restaurantId,
                                                            String cardNumberOrBarcode, Pageable pageable) {
        GiftCard card = findCard(restaurantId, cardNumberOrBarcode);
        return transactionRepository.findByGiftCardIdOrderByCreatedAtDesc(card.getId(), pageable);
    }

    /**
     * List all gift cards for a restaurant.
     */
    public Page<GiftCard> listGiftCards(Long restaurantId, Pageable pageable) {
        return giftCardRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId, pageable);
    }

    /**
     * Scheduled task to expire gift cards.
     */
    @Scheduled(cron = "0 0 1 * * *") // Run at 1 AM daily
    @SchedulerLock(name = "gift-card-expiry", lockAtLeastFor = "PT30S")
    @Transactional
    public void expireGiftCards() {
        List<GiftCard> expiredCards = giftCardRepository.findExpiredCards(OffsetDateTime.now());
        for (GiftCard card : expiredCards) {
            card.setStatus(GiftCardStatus.EXPIRED);
            giftCardRepository.save(card);

            // Record expiration transaction if balance remains
            if (card.getCurrentBalance().compareTo(BigDecimal.ZERO) > 0) {
                GiftCardTransaction transaction = GiftCardTransaction.builder()
                    .giftCard(card)
                    .transactionType(GiftCardTransactionType.EXPIRATION)
                    .amount(card.getCurrentBalance())
                    .balanceBefore(card.getCurrentBalance())
                    .balanceAfter(BigDecimal.ZERO)
                    .notes("Card expired - balance forfeited")
                    .build();
                transactionRepository.save(transaction);

                card.setCurrentBalance(BigDecimal.ZERO);
                giftCardRepository.save(card);
            }

            log.info("Gift card {} expired", card.getCardNumber());
        }
    }

    private GiftCard findCard(Long restaurantId, String cardNumberOrBarcode) {
        return giftCardRepository.findByCardNumberOrBarcode(restaurantId, cardNumberOrBarcode)
            .orElseThrow(() -> new IllegalArgumentException("Gift card not found"));
    }

    private String generateCardNumber() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}
