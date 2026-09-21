package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.repository.AccountRepository;
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
import java.util.List;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createRestaurant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @InjectMocks private AccountService accountService;

    private Account account;

    @BeforeEach
    void setUp() {
        account = new Account();
        account.setId(1L);
        account.setName("Revenue");
        account.setType(Account.AccountType.REVENUE);
        account.setBalance(BigDecimal.ZERO);
        account.setRestaurant(createRestaurant());
    }

    @Test
    @DisplayName("createAccount — saves")
    void create_saves() {
        when(accountRepository.save(any())).thenReturn(account);
        assertNotNull(accountService.createAccount(account));
    }

    @Test
    @DisplayName("getAccountById — found")
    void getById_found() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        assertEquals("Revenue", accountService.getAccountById(1L).getName());
    }

    @Test
    @DisplayName("getAccountById — not found throws")
    void getById_notFound_throws() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(Exception.class, () -> accountService.getAccountById(99L));
    }

    @Test
    @DisplayName("getAccountsByRestaurant — returns list")
    void getByRestaurant_returnsList() {
        when(accountRepository.findByRestaurant_IdAndActiveTrue(1L)).thenReturn(List.of(account));
        assertEquals(1, accountService.getAccountsByRestaurant(1L).size());
    }

    @Test
    @DisplayName("getAccountsByType — returns filtered")
    void getByType_returnsFiltered() {
        when(accountRepository.findByRestaurant_IdAndType(1L, Account.AccountType.REVENUE))
                .thenReturn(List.of(account));
        assertEquals(1, accountService.getAccountsByType(1L, Account.AccountType.REVENUE).size());
    }

    @Test
    @DisplayName("deleteAccount — soft deletes")
    void delete_softDeletes() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(accountRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        accountService.deleteAccount(1L, "admin");
        verify(accountRepository).save(any());
    }
}
