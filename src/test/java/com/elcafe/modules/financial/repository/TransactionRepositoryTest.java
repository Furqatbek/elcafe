package com.elcafe.modules.financial.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.entity.Transaction;
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
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class TransactionRepositoryTest {

    @Autowired private TransactionRepository transactionRepository;
    @Autowired private EntityManager em;

    private Restaurant restaurant;
    private Account assetAccount;
    private Account expenseAccount;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setActive(true);
        em.persist(restaurant);

        assetAccount = Account.builder()
                .restaurant(restaurant)
                .code("1000")
                .name("Cash")
                .type(Account.AccountType.ASSET)
                .category(Account.AccountCategory.CASH)
                .balance(BigDecimal.ZERO)
                .normalBalance(Account.NormalBalance.DEBIT)
                .active(true)
                .systemAccount(false)
                .build();
        em.persist(assetAccount);

        expenseAccount = Account.builder()
                .restaurant(restaurant)
                .code("5000")
                .name("Supplies Expense")
                .type(Account.AccountType.EXPENSE)
                .category(Account.AccountCategory.SUPPLIES)
                .balance(BigDecimal.ZERO)
                .normalBalance(Account.NormalBalance.DEBIT)
                .active(true)
                .systemAccount(false)
                .build();
        em.persist(expenseAccount);
    }

    private Transaction createTransaction(Account account, LocalDate date,
                                          Transaction.TransactionType type, BigDecimal amount) {
        Transaction txn = Transaction.builder()
                .restaurant(restaurant)
                .account(account)
                .transactionDate(date)
                .type(type)
                .amount(amount)
                .build();
        em.persist(txn);
        return txn;
    }

    @Test
    @DisplayName("findByRestaurantAndDateRangeOrderByDate returns transactions in range ordered by date DESC")
    void findByRestaurantAndDateRangeOrderByDate() {
        createTransaction(assetAccount, LocalDate.of(2025, 1, 5), Transaction.TransactionType.DEBIT, new BigDecimal("100.00"));
        createTransaction(assetAccount, LocalDate.of(2025, 1, 20), Transaction.TransactionType.CREDIT, new BigDecimal("200.00"));
        createTransaction(expenseAccount, LocalDate.of(2025, 1, 10), Transaction.TransactionType.DEBIT, new BigDecimal("150.00"));
        createTransaction(assetAccount, LocalDate.of(2025, 2, 5), Transaction.TransactionType.DEBIT, new BigDecimal("300.00")); // outside range

        em.flush();
        em.clear();

        List<Transaction> results = transactionRepository.findByRestaurantAndDateRangeOrderByDate(
                restaurant.getId(), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31));
        assertEquals(3, results.size());
        // Verify DESC ordering by transactionDate
        assertTrue(results.get(0).getTransactionDate().isAfter(results.get(1).getTransactionDate())
                || results.get(0).getTransactionDate().isEqual(results.get(1).getTransactionDate()));
        assertTrue(results.get(1).getTransactionDate().isAfter(results.get(2).getTransactionDate())
                || results.get(1).getTransactionDate().isEqual(results.get(2).getTransactionDate()));
    }

    @Test
    @DisplayName("findByRestaurantAndDateRangeAndAccountType filters by account type")
    void findByRestaurantAndDateRangeAndAccountType() {
        createTransaction(assetAccount, LocalDate.of(2025, 1, 10), Transaction.TransactionType.DEBIT, new BigDecimal("100.00"));
        createTransaction(assetAccount, LocalDate.of(2025, 1, 15), Transaction.TransactionType.CREDIT, new BigDecimal("200.00"));
        createTransaction(expenseAccount, LocalDate.of(2025, 1, 12), Transaction.TransactionType.DEBIT, new BigDecimal("150.00"));

        em.flush();
        em.clear();

        List<Transaction> assetResults = transactionRepository.findByRestaurantAndDateRangeAndAccountType(
                restaurant.getId(), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31),
                Account.AccountType.ASSET);
        assertEquals(2, assetResults.size());
        assertTrue(assetResults.stream().allMatch(t -> t.getAccount().getType() == Account.AccountType.ASSET));

        List<Transaction> expenseResults = transactionRepository.findByRestaurantAndDateRangeAndAccountType(
                restaurant.getId(), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31),
                Account.AccountType.EXPENSE);
        assertEquals(1, expenseResults.size());
    }
}
