package com.elcafe.modules.financial.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.service.AccountService;
import com.elcafe.modules.financial.service.FinancialMigrationService;
import com.elcafe.modules.financial.service.RevenueService;
import com.elcafe.modules.financial.repository.JournalEntryRepository;
import com.elcafe.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    private MockMvc mockMvc;
    @Mock private AccountService accountService;
    @Mock private RevenueService revenueService;
    @Mock private FinancialMigrationService migrationService;
    @Mock private OrderRepository orderRepository;
    @Mock private JournalEntryRepository journalEntryRepository;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @InjectMocks private AccountController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test @DisplayName("GET / returns accounts") void getAll() throws Exception {
        when(accountService.getAccountsByRestaurant(1L)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/financial/accounts").param("restaurantId", "1")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /{id}") void getById() throws Exception {
        when(accountService.getAccountById(1L)).thenReturn(new Account());
        mockMvc.perform(get("/api/v1/financial/accounts/1")).andExpect(status().isOk());
    }
}
