package com.elcafe.modules.courier.service;

import com.elcafe.modules.courier.entity.CourierProfile;
import com.elcafe.modules.courier.entity.CourierWallet;
import com.elcafe.modules.courier.entity.CourierWalletTransaction;
import com.elcafe.modules.courier.enums.WalletTransactionType;
import com.elcafe.modules.courier.repository.CourierProfileRepository;
import com.elcafe.modules.courier.repository.CourierWalletRepository;
import com.elcafe.modules.courier.repository.CourierWalletTransactionRepository;
import com.elcafe.modules.order.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Service for managing courier wallet and transactions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourierWalletService {

    private final CourierWalletRepository walletRepository;
    private final CourierWalletTransactionRepository transactionRepository;
    private final CourierProfileRepository courierProfileRepository;

    // Default delivery fee calculation parameters
    private static final BigDecimal BASE_DELIVERY_FEE = new BigDecimal("5.00");
    private static final BigDecimal FEE_PERCENTAGE = new BigDecimal("0.15"); // 15% of order total
    private static final BigDecimal MAX_DELIVERY_FEE = new BigDecimal("20.00");

    /**
     * Credit courier wallet after successful delivery
     * Automatically calculates delivery fee and creates transaction
     */
    @Transactional
    public void creditDeliveryFee(Long courierId, Order order) {
        CourierProfile courier = courierProfileRepository.findById(courierId)
                .orElseThrow(() -> new RuntimeException("Courier not found: " + courierId));

        // Get or create wallet
        CourierWallet wallet = walletRepository.findByCourierProfileId(courierId)
                .orElseGet(() -> createWallet(courier));

        // Calculate delivery fee
        BigDecimal deliveryFee = calculateDeliveryFee(order);

        // Record current balance
        BigDecimal balanceBefore = wallet.getBalance();

        // Update wallet
        wallet.setBalance(wallet.getBalance().add(deliveryFee));
        wallet.setTotalEarned(wallet.getTotalEarned().add(deliveryFee));
        walletRepository.save(wallet);

        // Create transaction record
        CourierWalletTransaction transaction = CourierWalletTransaction.builder()
                .wallet(wallet)
                .courierId(courierId)
                .orderId(order.getId())
                .transactionType(WalletTransactionType.DELIVERY_FEE)
                .amount(deliveryFee)
                .balanceBefore(balanceBefore)
                .balanceAfter(wallet.getBalance())
                .description("Delivery fee for order #" + order.getOrderNumber())
                .createdBy("SYSTEM")
                .build();
        transactionRepository.save(transaction);

        log.info("Credited {} to courier {} wallet for order {}. New balance: {}",
                deliveryFee, courierId, order.getOrderNumber(), wallet.getBalance());
    }

    /**
     * Calculate delivery fee based on order
     * Can be customized based on business logic
     */
    private BigDecimal calculateDeliveryFee(Order order) {
        // Base fee + percentage of order total
        BigDecimal percentageFee = order.getTotal()
                .multiply(FEE_PERCENTAGE)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalFee = BASE_DELIVERY_FEE.add(percentageFee);

        // Cap at maximum
        if (totalFee.compareTo(MAX_DELIVERY_FEE) > 0) {
            totalFee = MAX_DELIVERY_FEE;
        }

        return totalFee;
    }

    /**
     * Add bonus to courier wallet
     */
    @Transactional
    public void addBonus(Long courierId, BigDecimal amount, String description) {
        addTransaction(courierId, null, WalletTransactionType.BONUS, amount, description, "ADMIN");
    }

    /**
     * Add fine to courier wallet (deducts from balance)
     */
    @Transactional
    public void addFine(Long courierId, BigDecimal amount, String description) {
        CourierProfile courier = courierProfileRepository.findById(courierId)
                .orElseThrow(() -> new RuntimeException("Courier not found: " + courierId));

        CourierWallet wallet = walletRepository.findByCourierProfileId(courierId)
                .orElseGet(() -> createWallet(courier));

        BigDecimal balanceBefore = wallet.getBalance();

        // Deduct fine from balance
        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setTotalFines(wallet.getTotalFines().add(amount));
        walletRepository.save(wallet);

        // Create transaction record (negative amount)
        CourierWalletTransaction transaction = CourierWalletTransaction.builder()
                .wallet(wallet)
                .courierId(courierId)
                .transactionType(WalletTransactionType.FINE)
                .amount(amount.negate())
                .balanceBefore(balanceBefore)
                .balanceAfter(wallet.getBalance())
                .description(description)
                .createdBy("ADMIN")
                .build();
        transactionRepository.save(transaction);

        log.info("Deducted fine of {} from courier {} wallet. New balance: {}",
                amount, courierId, wallet.getBalance());
    }

    /**
     * Process withdrawal request
     */
    @Transactional
    public void processWithdrawal(Long courierId, BigDecimal amount, String reference) {
        CourierProfile courier = courierProfileRepository.findById(courierId)
                .orElseThrow(() -> new RuntimeException("Courier not found: " + courierId));

        CourierWallet wallet = walletRepository.findByCourierProfileId(courierId)
                .orElseThrow(() -> new RuntimeException("Wallet not found for courier: " + courierId));

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient balance for withdrawal");
        }

        BigDecimal balanceBefore = wallet.getBalance();

        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setTotalWithdrawn(wallet.getTotalWithdrawn().add(amount));
        walletRepository.save(wallet);

        CourierWalletTransaction transaction = CourierWalletTransaction.builder()
                .wallet(wallet)
                .courierId(courierId)
                .transactionType(WalletTransactionType.WITHDRAWAL)
                .amount(amount.negate())
                .balanceBefore(balanceBefore)
                .balanceAfter(wallet.getBalance())
                .description("Withdrawal to bank account")
                .reference(reference)
                .createdBy("SYSTEM")
                .build();
        transactionRepository.save(transaction);

        log.info("Processed withdrawal of {} for courier {}. New balance: {}",
                amount, courierId, wallet.getBalance());
    }

    /**
     * Generic method to add any transaction type
     */
    @Transactional
    private void addTransaction(Long courierId, Long orderId, WalletTransactionType type,
                                BigDecimal amount, String description, String createdBy) {
        CourierProfile courier = courierProfileRepository.findById(courierId)
                .orElseThrow(() -> new RuntimeException("Courier not found: " + courierId));

        CourierWallet wallet = walletRepository.findByCourierProfileId(courierId)
                .orElseGet(() -> createWallet(courier));

        BigDecimal balanceBefore = wallet.getBalance();

        wallet.setBalance(wallet.getBalance().add(amount));
        if (type == WalletTransactionType.BONUS) {
            wallet.setTotalBonuses(wallet.getTotalBonuses().add(amount));
        }
        walletRepository.save(wallet);

        CourierWalletTransaction transaction = CourierWalletTransaction.builder()
                .wallet(wallet)
                .courierId(courierId)
                .orderId(orderId)
                .transactionType(type)
                .amount(amount)
                .balanceBefore(balanceBefore)
                .balanceAfter(wallet.getBalance())
                .description(description)
                .createdBy(createdBy)
                .build();
        transactionRepository.save(transaction);
    }

    /**
     * Create new wallet for courier
     */
    private CourierWallet createWallet(CourierProfile courier) {
        CourierWallet wallet = CourierWallet.builder()
                .courierProfile(courier)
                .balance(BigDecimal.ZERO)
                .totalEarned(BigDecimal.ZERO)
                .totalWithdrawn(BigDecimal.ZERO)
                .totalBonuses(BigDecimal.ZERO)
                .totalFines(BigDecimal.ZERO)
                .build();

        CourierWallet savedWallet = walletRepository.save(wallet);
        log.info("Created new wallet for courier {}", courier.getId());
        return savedWallet;
    }

    /**
     * Get courier wallet
     */
    public CourierWallet getWallet(Long courierId) {
        return walletRepository.findByCourierProfileId(courierId)
                .orElseThrow(() -> new RuntimeException("Wallet not found for courier: " + courierId));
    }
}
