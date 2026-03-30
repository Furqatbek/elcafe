package com.elcafe.modules.financial.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.financial.entity.PayrollEntry;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.repository.PayrollEntryRepository;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayrollServiceTest {

    @Mock private PayrollEntryRepository payrollRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private JournalService journalService;

    @InjectMocks private PayrollService payrollService;

    private PayrollEntry payroll;
    private Restaurant restaurant;
    private User employee;

    @BeforeEach
    void setUp() {
        restaurant = createRestaurant();
        employee = new User();
        employee.setId(1L);

        payroll = PayrollEntry.builder()
                .id(1L)
                .restaurant(restaurant)
                .employee(employee)
                .payPeriodStart(LocalDate.now().minusDays(30))
                .payPeriodEnd(LocalDate.now())
                .netPay(BigDecimal.valueOf(3000000))
                .grossPay(BigDecimal.valueOf(3500000))
                .status(PayrollEntry.PaymentStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("createPayrollEntry — saves and returns")
    void create_saves() {
        when(payrollRepository.save(any(PayrollEntry.class))).thenReturn(payroll);
        when(payrollRepository.count()).thenReturn(0L);
        assertNotNull(payrollService.createPayrollEntry(payroll));
    }

    @Test
    @DisplayName("getPayrollEntryById — found")
    void getById_found() {
        when(payrollRepository.findById(1L)).thenReturn(Optional.of(payroll));
        assertEquals(1L, payrollService.getPayrollEntryById(1L).getId());
    }

    @Test
    @DisplayName("getPayrollEntryById — not found throws")
    void getById_notFound_throws() {
        when(payrollRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(Exception.class, () -> payrollService.getPayrollEntryById(99L));
    }

    @Test
    @DisplayName("approvePayrollEntry — sets approved")
    void approve_setsApproved() {
        when(payrollRepository.findById(1L)).thenReturn(Optional.of(payroll));
        when(payrollRepository.save(any(PayrollEntry.class))).thenAnswer(i -> i.getArgument(0));

        PayrollEntry result = payrollService.approvePayrollEntry(1L, "admin");
        assertEquals(PayrollEntry.PaymentStatus.APPROVED, result.getStatus());
    }

    @Test
    @DisplayName("getPayrollEntriesByRestaurant — returns list")
    void getByRestaurant_returns() {
        when(payrollRepository.findByRestaurant_Id(1L)).thenReturn(List.of(payroll));
        assertEquals(1, payrollService.getPayrollEntriesByRestaurant(1L).size());
    }

    @Test
    @DisplayName("getPayrollEntriesByDateRange — returns filtered")
    void getByDateRange_returns() {
        LocalDate start = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now();
        when(payrollRepository.findByRestaurant_IdAndPayPeriodStartBetween(1L, start, end))
                .thenReturn(List.of(payroll));
        assertEquals(1, payrollService.getPayrollEntriesByDateRange(1L, start, end).size());
    }

    @Test
    @DisplayName("getPendingPayrolls — returns pending")
    void getPending_returns() {
        when(payrollRepository.findByRestaurant_IdAndStatus(1L, PayrollEntry.PaymentStatus.PENDING))
                .thenReturn(List.of(payroll));
        assertEquals(1, payrollService.getPendingPayrolls(1L).size());
    }
}
