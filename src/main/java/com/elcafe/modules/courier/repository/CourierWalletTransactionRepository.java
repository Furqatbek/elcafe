package com.elcafe.modules.courier.repository;

import com.elcafe.modules.courier.entity.CourierWalletTransaction;
import com.elcafe.modules.courier.enums.WalletTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CourierWalletTransactionRepository extends JpaRepository<CourierWalletTransaction, Long> {

    /**
     * Get all transactions for a wallet
     */
    List<CourierWalletTransaction> findByWalletIdOrderByCreatedAtDesc(Long walletId);

    /**
     * Get all transactions for a courier
     */
    List<CourierWalletTransaction> findByCourierIdOrderByCreatedAtDesc(Long courierId);

    /**
     * Get transactions for a courier within date range
     */
    @Query("SELECT t FROM CourierWalletTransaction t WHERE t.courierId = :courierId " +
            "AND t.createdAt BETWEEN :startDate AND :endDate ORDER BY t.createdAt DESC")
    List<CourierWalletTransaction> findByCourierIdAndDateRange(
            @Param("courierId") Long courierId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * Get transactions by type for a courier
     */
    List<CourierWalletTransaction> findByCourierIdAndTransactionTypeOrderByCreatedAtDesc(
            Long courierId,
            WalletTransactionType transactionType
    );

    /**
     * Get transactions for a specific order
     */
    List<CourierWalletTransaction> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    /**
     * Calculate total earnings for a courier
     */
    @Query("SELECT SUM(t.amount) FROM CourierWalletTransaction t WHERE t.courierId = :courierId " +
            "AND t.transactionType IN ('DELIVERY_FEE', 'BONUS', 'TIP', 'COMPENSATION')")
    BigDecimal getTotalEarnings(@Param("courierId") Long courierId);

    /**
     * Calculate total withdrawals for a courier
     */
    @Query("SELECT SUM(t.amount) FROM CourierWalletTransaction t WHERE t.courierId = :courierId " +
            "AND t.transactionType = 'WITHDRAWAL'")
    BigDecimal getTotalWithdrawals(@Param("courierId") Long courierId);
}
