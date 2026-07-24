package com.elcafe.modules.financial.controller;

import com.elcafe.modules.financial.entity.Expense;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.service.ExpenseService;
import com.elcafe.modules.order.service.IdempotencyService;
import com.elcafe.modules.pos.shift.repository.EmployeeShiftRepository;
import com.elcafe.modules.pos.shift.service.ShiftEnforcementService;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.waiter.helper.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExpenseControllerTest {

    private MockMvc mockMvc;
    @Mock private ExpenseService expenseService;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ShiftEnforcementService shiftEnforcementService;
    @Mock private EmployeeShiftRepository employeeShiftRepository;
    @Mock private IdempotencyService idempotencyService;
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
        when(expenseService.getExpenseById(1L)).thenReturn(Expense.builder().id(1L).amount(BigDecimal.ZERO)
                .restaurant(TestDataFactory.createRestaurant()).build());
        mockMvc.perform(get("/api/v1/financial/expenses/1")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /unpaid") void getUnpaid() throws Exception {
        when(expenseService.getUnpaidExpenses(1L)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/financial/expenses/unpaid").param("restaurantId", "1")).andExpect(status().isOk());
    }

    // ---- P0-1: pagination is opt-in via page/size ----

    // Asserted directly on the returned object rather than through MockMvc:
    // standalone MockMvc lacks the Spring Data Page serialization config the
    // real app context has (other controllers return Page and serialize fine).

    @Test @DisplayName("GET with page/size returns a paginated Page, not a List")
    void getPaginated() {
        when(expenseService.getExpensesByRestaurant(eq(1L), any(Pageable.class))).thenReturn(Page.empty());
        var resp = controller.getExpenses(1L, null, null, null, null, 0, 20);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().getData()).isInstanceOf(Page.class);
    }

    @Test @DisplayName("GET with from/to + page uses the paginated date-range query")
    void getPaginatedDateRange() {
        when(expenseService.getExpensesByDateRange(eq(1L), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());
        var resp = controller.getExpenses(1L, null, null,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 0, 50);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().getData()).isInstanceOf(Page.class);
        verify(expenseService).getExpensesByDateRange(eq(1L), any(), any(), any(Pageable.class));
    }

    @Test @DisplayName("GET without page/size stays a legacy List (backwards compatible)")
    void getLegacyList() throws Exception {
        when(expenseService.getExpensesByRestaurant(1L)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/financial/expenses").param("restaurantId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ---- P0-2: autoApprove create-and-approve in one call ----

    @Test @DisplayName("POST with autoApprove=true approves the expense in the same call")
    void createAutoApprove() throws Exception {
        stubIdempotencyPassthrough();
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(TestDataFactory.createRestaurant()));
        Expense created = Expense.builder().id(5L).amount(BigDecimal.TEN)
                .restaurant(TestDataFactory.createRestaurant()).build();
        when(expenseService.createExpense(any())).thenReturn(created);
        when(expenseService.approveExpense(eq(5L), anyString())).thenReturn(created);

        mockMvc.perform(post("/api/v1/financial/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson(true)))
                .andExpect(status().isCreated());

        verify(expenseService).approveExpense(eq(5L), anyString());
    }

    @Test @DisplayName("POST without autoApprove does NOT approve (unchanged two-step flow)")
    void createNoAutoApprove() throws Exception {
        stubIdempotencyPassthrough();
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(TestDataFactory.createRestaurant()));
        Expense created = Expense.builder().id(6L).amount(BigDecimal.TEN)
                .restaurant(TestDataFactory.createRestaurant()).build();
        when(expenseService.createExpense(any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/financial/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson(false)))
                .andExpect(status().isCreated());

        verify(expenseService, never()).approveExpense(anyLong(), anyString());
    }

    /** Make the idempotency wrapper just run the supplied operation. */
    private void stubIdempotencyPassthrough() {
        when(idempotencyService.executeIdempotently(any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    Supplier<?> op = inv.getArgument(3);
                    return IdempotencyService.IdempotentResult.newResult(op.get());
                });
    }

    private String expenseJson(boolean autoApprove) {
        return "{"
                + "\"restaurantId\":1,"
                + "\"expenseDate\":\"2026-07-24\","
                + "\"category\":\"SUPPLIES\","
                + "\"description\":\"napkins\","
                + "\"amount\":100,"
                + "\"paymentMethod\":\"CASH\","
                + "\"autoApprove\":" + autoApprove
                + "}";
    }
}
