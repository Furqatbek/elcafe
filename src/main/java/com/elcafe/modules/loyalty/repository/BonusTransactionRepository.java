package com.elcafe.modules.loyalty.repository;

import com.elcafe.modules.loyalty.entity.BonusTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BonusTransactionRepository extends JpaRepository<BonusTransaction, Long> {

    Optional<BonusTransaction> findByIdempotencyKey(String idempotencyKey);

    Page<BonusTransaction> findByCustomerLoyaltyIdOrderByCreatedAtDesc(Long customerLoyaltyId, Pageable pageable);

    List<BonusTransaction> findByOrderId(Long orderId);

    @Query("SELECT SUM(bt.amount) FROM BonusTransaction bt WHERE " +
           "bt.customerLoyalty.id = :customerLoyaltyId AND " +
           "bt.transactionType IN ('EARNED', 'REFUNDED', 'BIRTHDAY_BONUS', 'FIRST_ORDER_BONUS', 'REACTIVATION_BONUS', 'PROMOTION_BONUS')")
    BigDecimal calculateTotalEarned(@Param("customerLoyaltyId") Long customerLoyaltyId);

    @Query("SELECT SUM(bt.amount) FROM BonusTransaction bt WHERE " +
           "bt.customerLoyalty.id = :customerLoyaltyId AND " +
           "bt.transactionType = 'SPENT'")
    BigDecimal calculateTotalSpent(@Param("customerLoyaltyId") Long customerLoyaltyId);

    @Query("SELECT bt FROM BonusTransaction bt WHERE " +
           "bt.createdAt < :expiryDate AND " +
           "bt.transactionType IN ('EARNED', 'REFUNDED', 'BIRTHDAY_BONUS', 'FIRST_ORDER_BONUS', 'REACTIVATION_BONUS', 'PROMOTION_BONUS')")
    List<BonusTransaction> findExpiredBonuses(@Param("expiryDate") LocalDateTime expiryDate);
}
