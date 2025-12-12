package com.elcafe.modules.financial.repository;

import com.elcafe.modules.financial.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findByRestaurantId(Long restaurantId);

    Page<Transaction> findByRestaurantId(Long restaurantId, Pageable pageable);

    List<Transaction> findByAccountId(Long accountId);

    Page<Transaction> findByAccountId(Long accountId, Pageable pageable);

    List<Transaction> findByRestaurantIdAndTransactionDateBetween(
            Long restaurantId, LocalDate startDate, LocalDate endDate);

    List<Transaction> findByAccountIdAndTransactionDateBetween(
            Long accountId, LocalDate startDate, LocalDate endDate);

    List<Transaction> findByReferenceTypeAndReferenceId(String referenceType, Long referenceId);

    @Query("SELECT t FROM FinancialTransaction t WHERE t.restaurant.id = :restaurantId " +
           "AND t.transactionDate BETWEEN :startDate AND :endDate " +
           "AND t.account.type = :accountType")
    List<Transaction> findByRestaurantAndDateRangeAndAccountType(
            Long restaurantId, LocalDate startDate, LocalDate endDate,
            com.elcafe.modules.financial.entity.Account.AccountType accountType);

    @Query("SELECT t FROM FinancialTransaction t WHERE t.restaurant.id = :restaurantId " +
           "AND t.transactionDate BETWEEN :startDate AND :endDate " +
           "ORDER BY t.transactionDate DESC, t.createdAt DESC")
    List<Transaction> findByRestaurantAndDateRangeOrderByDate(
            Long restaurantId, LocalDate startDate, LocalDate endDate);
}
