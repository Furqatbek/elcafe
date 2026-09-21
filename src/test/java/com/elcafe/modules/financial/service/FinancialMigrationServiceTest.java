package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.repository.ExpenseRepository;
import com.elcafe.modules.financial.repository.JournalEntryRepository;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createRestaurant;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialMigrationServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ExpenseRepository expenseRepository;
    @Mock private JournalEntryRepository journalEntryRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private RevenueService revenueService;
    @Mock private AccountService accountService;
    @Mock private JournalService journalService;

    @InjectMocks private FinancialMigrationService migrationService;

    @Test
    @DisplayName("syncRestaurantFinancialData — returns migration result")
    void sync_returnsResult() {
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(createRestaurant()));
        when(accountRepository.existsByRestaurant_Id(1L)).thenReturn(true);
        when(orderRepository.findByRestaurant_IdAndStatus(anyLong(), any())).thenReturn(List.of());
        when(expenseRepository.findByRestaurant_Id(1L)).thenReturn(List.of());

        var result = migrationService.syncRestaurantFinancialData(1L);
        assertNotNull(result);
    }

    @Test
    @DisplayName("syncAllRestaurants — processes all restaurants")
    void syncAll_processesAll() {
        when(restaurantRepository.findAll()).thenReturn(List.of(createRestaurant()));
        when(restaurantRepository.findById(anyLong())).thenReturn(Optional.of(createRestaurant()));
        when(accountRepository.existsByRestaurant_Id(anyLong())).thenReturn(true);
        when(orderRepository.findByRestaurant_IdAndStatus(anyLong(), any())).thenReturn(List.of());
        when(expenseRepository.findByRestaurant_Id(anyLong())).thenReturn(List.of());

        List<FinancialMigrationService.MigrationResult> results = migrationService.syncAllRestaurants();
        assertNotNull(results);
    }
}
