package com.elcafe.modules.financial.service;

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
import static org.mockito.ArgumentMatchers.anyLong;
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

    @BeforeEach
    void setUp() {
        payroll = new PayrollEntry();
        payroll.setId(1L);
        payroll.setRestaurant(createRestaurant());
        payroll.setNetPay(BigDecimal.valueOf(3000000));
        payroll.setPayPeriodStart(LocalDate.now().minusDays(30));
        payroll.setPayPeriodEnd(LocalDate.now());
        payroll.setStatus(PayrollEntry.PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("createPayrollEntry — saves and returns")
    void create_saves() {
        when(payrollRepository.save(any(PayrollEntry.class))).thenReturn(payroll);
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
    @DisplayName("getPayrollEntriesByDateRange — returns list")
    void getByDateRange_returns() {
        LocalDate start = LocalDate.now().minusDays(30);
        LocalDate end = LocalDate.now();
        when(payrollRepository.findByRestaurant_IdAndPayPeriodStartBetween(1L, start, end))
                .thenReturn(List.of(payroll));
        assertEquals(1, payrollService.getPayrollEntriesByDateRange(1L, start, end).size());
    }

    @Test
    @DisplayName("getTotalPayrollByDateRange — returns sum")
    void getTotalByDateRange_returnsSum() {
        when(payrollRepository.getTotalPayrollByDateRange(anyLong(), any(), any()))
                .thenReturn(BigDecimal.valueOf(9000000));
        assertEquals(0, BigDecimal.valueOf(9000000).compareTo(
                payrollService.getTotalPayrollByDateRange(1L, LocalDate.now().minusDays(30), LocalDate.now())));
    }

    @Test
    @DisplayName("getPendingPayrolls — returns pending only")
    void getPending_returns() {
        when(payrollRepository.findPendingPayrolls(1L))
                .thenReturn(List.of(payroll));
        assertEquals(1, payrollService.getPendingPayrolls(1L).size());
    }

    @Test
    @DisplayName("deletePayrollEntry — soft deletes")
    void delete_softDeletes() {
        when(payrollRepository.findById(1L)).thenReturn(Optional.of(payroll));
        when(payrollRepository.save(any(PayrollEntry.class))).thenAnswer(i -> i.getArgument(0));
        payrollService.deletePayrollEntry(1L, "admin");
        verify(payrollRepository).save(any(PayrollEntry.class));
    }
}
