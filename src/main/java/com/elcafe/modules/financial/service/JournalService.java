package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.entity.JournalEntry;
import com.elcafe.modules.financial.entity.Transaction;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.repository.JournalEntryRepository;
import com.elcafe.modules.financial.repository.TransactionRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JournalService {

    private final JournalEntryRepository journalEntryRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final RestaurantRepository restaurantRepository;

    /**
     * Create a journal entry with debit and credit transactions
     */
    @Transactional
    public JournalEntry createJournalEntry(Long restaurantId, LocalDate entryDate, String description,
                                          String referenceType, Long referenceId,
                                          Long debitAccountId, Long creditAccountId,
                                          BigDecimal amount, String createdBy) {
        log.info("Creating journal entry for restaurant: {}, amount: {}", restaurantId, amount);

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        Account debitAccount = accountRepository.findById(debitAccountId)
                .orElseThrow(() -> new RuntimeException("Debit account not found"));

        Account creditAccount = accountRepository.findById(creditAccountId)
                .orElseThrow(() -> new RuntimeException("Credit account not found"));

        // Generate entry number
        String entryNumber = generateEntryNumber(restaurantId);

        // Create journal entry
        JournalEntry journalEntry = JournalEntry.builder()
                .restaurant(restaurant)
                .entryNumber(entryNumber)
                .entryDate(entryDate)
                .description(description)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .totalDebit(amount)
                .totalCredit(amount)
                .balanced(true)
                .status(JournalEntry.Status.DRAFT)
                .createdBy(createdBy)
                .build();

        journalEntry = journalEntryRepository.save(journalEntry);

        // Create debit transaction
        BigDecimal debitBalanceBefore = debitAccount.getBalance();
        debitAccount.updateBalance(amount, Account.TransactionType.DEBIT);
        BigDecimal debitBalanceAfter = debitAccount.getBalance();
        accountRepository.save(debitAccount);

        Transaction debitTransaction = Transaction.builder()
                .restaurant(restaurant)
                .account(debitAccount)
                .journalEntry(journalEntry)
                .transactionDate(entryDate)
                .type(Transaction.TransactionType.DEBIT)
                .amount(amount)
                .balanceBefore(debitBalanceBefore)
                .balanceAfter(debitBalanceAfter)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .description(description)
                .performedBy(createdBy)
                .build();

        transactionRepository.save(debitTransaction);

        // Create credit transaction
        BigDecimal creditBalanceBefore = creditAccount.getBalance();
        creditAccount.updateBalance(amount, Account.TransactionType.CREDIT);
        BigDecimal creditBalanceAfter = creditAccount.getBalance();
        accountRepository.save(creditAccount);

        Transaction creditTransaction = Transaction.builder()
                .restaurant(restaurant)
                .account(creditAccount)
                .journalEntry(journalEntry)
                .transactionDate(entryDate)
                .type(Transaction.TransactionType.CREDIT)
                .amount(amount)
                .balanceBefore(creditBalanceBefore)
                .balanceAfter(creditBalanceAfter)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .description(description)
                .performedBy(createdBy)
                .build();

        transactionRepository.save(creditTransaction);

        log.info("Journal entry created: {}", entryNumber);
        return journalEntry;
    }

    /**
     * Post a draft journal entry (make it permanent)
     */
    @Transactional
    public JournalEntry postJournalEntry(Long journalEntryId, String approvedBy) {
        log.info("Posting journal entry: {}", journalEntryId);

        JournalEntry journalEntry = journalEntryRepository.findById(journalEntryId)
                .orElseThrow(() -> new RuntimeException("Journal entry not found"));

        if (journalEntry.getStatus() != JournalEntry.Status.DRAFT) {
            throw new RuntimeException("Only draft entries can be posted");
        }

        if (!journalEntry.isBalanced()) {
            throw new RuntimeException("Journal entry is not balanced - cannot post");
        }

        journalEntry.setStatus(JournalEntry.Status.POSTED);
        journalEntry.setApprovedBy(approvedBy);
        journalEntry.setApprovedAt(LocalDateTime.now());

        return journalEntryRepository.save(journalEntry);
    }

    /**
     * Reverse a posted journal entry
     */
    @Transactional
    public JournalEntry reverseJournalEntry(Long journalEntryId, String reversedBy) {
        log.info("Reversing journal entry: {}", journalEntryId);

        JournalEntry originalEntry = journalEntryRepository.findById(journalEntryId)
                .orElseThrow(() -> new RuntimeException("Journal entry not found"));

        if (originalEntry.getStatus() != JournalEntry.Status.POSTED) {
            throw new RuntimeException("Only posted entries can be reversed");
        }

        // Create reversing entry with opposite debits and credits
        List<Transaction> originalTransactions = transactionRepository.findByReferenceTypeAndReferenceId(
                "JOURNAL_ENTRY", journalEntryId);

        String entryNumber = generateEntryNumber(originalEntry.getRestaurant().getId());
        JournalEntry reversingEntry = JournalEntry.builder()
                .restaurant(originalEntry.getRestaurant())
                .entryNumber(entryNumber)
                .entryDate(LocalDate.now())
                .description("REVERSAL: " + originalEntry.getDescription())
                .referenceType("REVERSAL")
                .referenceId(originalEntry.getId())
                .totalDebit(originalEntry.getTotalDebit())
                .totalCredit(originalEntry.getTotalCredit())
                .balanced(true)
                .status(JournalEntry.Status.POSTED)
                .createdBy(reversedBy)
                .approvedBy(reversedBy)
                .approvedAt(LocalDateTime.now())
                .build();

        reversingEntry = journalEntryRepository.save(reversingEntry);

        // Create opposite transactions
        for (Transaction originalTx : originalTransactions) {
            Account account = originalTx.getAccount();
            BigDecimal balanceBefore = account.getBalance();

            // Reverse the transaction type
            Transaction.TransactionType reverseType = originalTx.getType() == Transaction.TransactionType.DEBIT
                    ? Transaction.TransactionType.CREDIT
                    : Transaction.TransactionType.DEBIT;

            account.updateBalance(originalTx.getAmount(),
                    reverseType == Transaction.TransactionType.DEBIT
                            ? Account.TransactionType.DEBIT
                            : Account.TransactionType.CREDIT);

            BigDecimal balanceAfter = account.getBalance();
            accountRepository.save(account);

            Transaction reversingTx = Transaction.builder()
                    .restaurant(originalEntry.getRestaurant())
                    .account(account)
                    .journalEntry(reversingEntry)
                    .transactionDate(LocalDate.now())
                    .type(reverseType)
                    .amount(originalTx.getAmount())
                    .balanceBefore(balanceBefore)
                    .balanceAfter(balanceAfter)
                    .referenceType("REVERSAL")
                    .referenceId(originalEntry.getId())
                    .description("REVERSAL: " + originalTx.getDescription())
                    .performedBy(reversedBy)
                    .build();

            transactionRepository.save(reversingTx);
        }

        // Mark original as reversed
        originalEntry.setStatus(JournalEntry.Status.REVERSED);
        journalEntryRepository.save(originalEntry);

        log.info("Journal entry reversed: {}", journalEntryId);
        return reversingEntry;
    }

    public List<JournalEntry> getJournalEntriesByRestaurant(Long restaurantId) {
        return journalEntryRepository.findByRestaurant_Id(restaurantId);
    }

    public List<JournalEntry> getJournalEntriesByDateRange(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        return journalEntryRepository.findPostedEntriesByDateRange(restaurantId, startDate, endDate);
    }

    public List<Transaction> getTransactionsByAccount(Long accountId) {
        return transactionRepository.findByAccount_Id(accountId);
    }

    public List<Transaction> getTransactionsByDateRange(Long restaurantId, LocalDate startDate, LocalDate endDate) {
        return transactionRepository.findByRestaurantAndDateRangeOrderByDate(restaurantId, startDate, endDate);
    }

    private String generateEntryNumber(Long restaurantId) {
        String datePrefix = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        long count = journalEntryRepository.findByRestaurant_Id(restaurantId).stream()
                .filter(je -> je.getEntryNumber().startsWith("JE-" + datePrefix))
                .count();
        return String.format("JE-%s-%04d", datePrefix, count + 1);
    }
}
