package com.elcafe.modules.loyalty.service;

import com.elcafe.modules.loyalty.entity.BonusTransaction;
import com.elcafe.modules.loyalty.entity.CustomerLoyalty;
import com.elcafe.modules.loyalty.repository.BonusTransactionRepository;
import com.elcafe.modules.order.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for handling all bonus transaction operations
 * Implements a ledger-style approach for bonus management
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BonusService {

    private final BonusTransactionRepository bonusTransactionRepository;

    /**
     * Record a bonus transaction (idempotent)
     */
    @Transactional
    public BonusTransaction recordTransaction(
            CustomerLoyalty customerLoyalty,
            BonusTransaction.TransactionType type,
            BigDecimal amount,
            Order order,
            String description,
            String idempotencyKey,
            Map<String, Object> metadata
    ) {
        log.info("Recording bonus transaction for customer {}: type={}, amount={}",
                customerLoyalty.getId(), type, amount);

        // Check idempotency
        if (idempotencyKey != null) {
            Optional<BonusTransaction> existing = bonusTransactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Transaction with idempotency key {} already exists, returning existing", idempotencyKey);
                return existing.get();
            }
        }

        // Apply the transaction to the loyalty balance
        BigDecimal newBalance;
        if (isCredit(type)) {
            customerLoyalty.addBonus(amount);
            newBalance = customerLoyalty.getCurrentBalance();
        } else if (isDebit(type)) {
            customerLoyalty.deductBonus(amount.abs());
            newBalance = customerLoyalty.getCurrentBalance();
        } else {
            throw new IllegalArgumentException("Unknown transaction type: " + type);
        }

        // Create and save the transaction record
        BonusTransaction transaction = BonusTransaction.builder()
                .customerLoyalty(customerLoyalty)
                .restaurantId(customerLoyalty.getRestaurantId()) // §3.7: inherit the loyalty's tenant
                .transactionType(type)
                .amount(amount)
                .balanceAfter(newBalance)
                .order(order)
                .description(description)
                .idempotencyKey(idempotencyKey)
                .metadata(metadata)
                .build();

        transaction = bonusTransactionRepository.save(transaction);
        log.info("Bonus transaction recorded: id={}, balance={}", transaction.getId(), newBalance);

        return transaction;
    }

    /**
     * Get transaction history for a customer
     */
    @Transactional(readOnly = true)
    public Page<BonusTransaction> getTransactionHistory(Long customerLoyaltyId, Pageable pageable) {
        return bonusTransactionRepository.findByCustomerLoyaltyIdOrderByCreatedAtDesc(customerLoyaltyId, pageable);
    }

    /**
     * Get transactions for a specific order
     */
    @Transactional(readOnly = true)
    public List<BonusTransaction> getTransactionsByOrder(Long orderId) {
        return bonusTransactionRepository.findByOrderId(orderId);
    }

    /**
     * Calculate total earned bonuses
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateTotalEarned(Long customerLoyaltyId) {
        BigDecimal total = bonusTransactionRepository.calculateTotalEarned(customerLoyaltyId);
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Calculate total spent bonuses
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateTotalSpent(Long customerLoyaltyId) {
        BigDecimal total = bonusTransactionRepository.calculateTotalSpent(customerLoyaltyId);
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Check if transaction type is credit (increases balance)
     */
    private boolean isCredit(BonusTransaction.TransactionType type) {
        return type == BonusTransaction.TransactionType.EARNED ||
               type == BonusTransaction.TransactionType.REFUNDED ||
               type == BonusTransaction.TransactionType.BIRTHDAY_BONUS ||
               type == BonusTransaction.TransactionType.FIRST_ORDER_BONUS ||
               type == BonusTransaction.TransactionType.REACTIVATION_BONUS ||
               type == BonusTransaction.TransactionType.PROMOTION_BONUS ||
               type == BonusTransaction.TransactionType.REFERRAL_BONUS ||
               type == BonusTransaction.TransactionType.TOP_UP;
    }

    /**
     * Check if transaction type is debit (decreases balance)
     */
    private boolean isDebit(BonusTransaction.TransactionType type) {
        return type == BonusTransaction.TransactionType.SPENT ||
               type == BonusTransaction.TransactionType.EXPIRED;
    }
}
