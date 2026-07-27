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

    /**
     * Welcome bonus credited per guest, for the staff-registration report.
     *
     * <p>Returns {@code [customerId, amount]} pairs so the report can attribute the money to the
     * employee who registered each guest. Reading the actual transactions rather than multiplying a
     * headcount by today's configured amount is the point: the config changes over time, and the number
     * an operator is checking is what was really credited, not what would be credited now.
     */
    @Query("SELECT bt.customerLoyalty.customer.id, SUM(bt.amount) FROM BonusTransaction bt "
            + "WHERE bt.customerLoyalty.customer.id IN :customerIds "
            + "AND bt.transactionType = com.elcafe.modules.loyalty.entity.BonusTransaction.TransactionType.REGISTRATION_BONUS "
            + "GROUP BY bt.customerLoyalty.customer.id")
    List<Object[]> sumRegistrationBonusByCustomer(@Param("customerIds") List<Long> customerIds);
}
