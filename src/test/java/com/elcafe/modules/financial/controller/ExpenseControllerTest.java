package com.elcafe.modules.financial.controller;

import com.elcafe.modules.financial.entity.Expense;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.service.ExpenseService;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
class ExpenseControllerTest {

    private MockMvc mockMvc;
    @Mock private ExpenseService expenseService;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private AccountRepository accountRepository;
    @InjectMocks private ExpenseController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test @DisplayName("GET / returns expenses") void getAll() throws Exception {
        when(expenseService.getExpensesByRestaurant(1L)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/financial/expenses").param("restaurantId", "1")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /{id}") void getById() throws Exception {
        when(expenseService.getExpenseById(1L)).thenReturn(new Expense());
        mockMvc.perform(get("/api/v1/financial/expenses/1")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /unpaid") void getUnpaid() throws Exception {
        when(expenseService.getUnpaidExpenses(1L)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/financial/expenses/unpaid").param("restaurantId", "1")).andExpect(status().isOk());
    }
}
