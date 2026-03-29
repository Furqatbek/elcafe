package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.entity.JournalEntry;
import com.elcafe.modules.financial.entity.Transaction;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.repository.JournalEntryRepository;
import com.elcafe.modules.financial.repository.TransactionRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createRestaurant;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JournalServiceTest {

    @Mock private JournalEntryRepository journalEntryRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private RestaurantRepository restaurantRepository;

    @InjectMocks private JournalService journalService;

    private Account debitAccount;
    private Account creditAccount;

    @BeforeEach
    void setUp() {
        debitAccount = new Account();
        debitAccount.setId(1L);
        debitAccount.setName("Cash");
        debitAccount.setBalance(BigDecimal.ZERO);

        creditAccount = new Account();
        creditAccount.setId(2L);
        creditAccount.setName("Revenue");
        creditAccount.setBalance(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("createJournalEntry — creates entry with transactions")
    void createJournalEntry_creates() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(createRestaurant()));
        when(accountRepository.findById(1L)).thenReturn(Optional.of(debitAccount));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(creditAccount));
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(i -> {
            JournalEntry e = i.getArgument(0); e.setId(1L); return e;
        });
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));

        JournalEntry result = journalService.createJournalEntry(
                1L, LocalDate.now(), "Test entry", 1L, 2L, BigDecimal.valueOf(50000), null);

        assertNotNull(result);
        verify(journalEntryRepository).save(any(JournalEntry.class));
    }

    @Test
    @DisplayName("getJournalEntriesByRestaurant — returns list")
    void getByRestaurant_returnsList() {
        when(journalEntryRepository.findByRestaurantIdOrderByEntryDateDesc(1L)).thenReturn(List.of());
        assertNotNull(journalService.getJournalEntriesByRestaurant(1L));
    }

    @Test
    @DisplayName("getJournalEntriesByDateRange — returns filtered")
    void getByDateRange_returnsFiltered() {
        when(journalEntryRepository.findByRestaurantIdAndEntryDateBetweenOrderByEntryDateDesc(anyLong(), any(), any()))
                .thenReturn(List.of());
        assertNotNull(journalService.getJournalEntriesByDateRange(1L, LocalDate.now().minusDays(7), LocalDate.now()));
    }

    @Test
    @DisplayName("getTransactionsByAccount — returns list")
    void getTransactionsByAccount_returnsList() {
        when(transactionRepository.findByAccountIdOrderByTransactionDateDesc(1L)).thenReturn(List.of());
        assertNotNull(journalService.getTransactionsByAccount(1L));
    }
}
