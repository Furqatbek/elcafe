package com.elcafe.modules.financial.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.financial.entity.PayrollEntry;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.repository.PayrollEntryRepository;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createRestaurant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayrollServiceTest {

    @Mock private PayrollEntryRepository payrollRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private JournalService journalService;
    @InjectMocks private PayrollService payrollService;

    private PayrollEntry payroll;

    @BeforeEach
    void setUp() {
        User employee = new User(); employee.setId(1L);
        payroll = PayrollEntry.builder().id(1L).restaurant(createRestaurant()).employee(employee)
                .payPeriodStart(LocalDate.now().minusDays(30)).payPeriodEnd(LocalDate.now())
                .netPay(BigDecimal.valueOf(3000000)).grossPay(BigDecimal.valueOf(3500000))
                .status(PayrollEntry.PaymentStatus.PENDING).build();
    }

    @Test @DisplayName("getById found") void getById() {
        when(payrollRepository.findById(1L)).thenReturn(Optional.of(payroll));
        assertEquals(1L, payrollService.getPayrollEntryById(1L).getId());
    }
    @Test @DisplayName("getById not found") void getById_notFound() {
        when(payrollRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(Exception.class, () -> payrollService.getPayrollEntryById(99L));
    }
    @Test @DisplayName("approve sets APPROVED") void approve() {
        when(payrollRepository.findById(1L)).thenReturn(Optional.of(payroll));
        when(payrollRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        assertEquals(PayrollEntry.PaymentStatus.APPROVED, payrollService.approvePayrollEntry(1L, "admin").getStatus());
    }
    @Test @DisplayName("getByRestaurant returns") void getByRestaurant() {
        when(payrollRepository.findByRestaurant_Id(1L)).thenReturn(List.of(payroll));
        assertEquals(1, payrollService.getPayrollEntriesByRestaurant(1L).size());
    }
    @Test @DisplayName("getPending returns") void getPending() {
        when(payrollRepository.findPendingPayrolls(1L)).thenReturn(List.of(payroll));
        assertEquals(1, payrollService.getPendingPayrolls(1L).size());
    }

    @Test @DisplayName("update mutates fields on PENDING entry") void update_pending() {
        when(payrollRepository.findById(1L)).thenReturn(Optional.of(payroll));
        when(payrollRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        PayrollEntry updated = payrollService.updatePayrollEntry(
                1L, BigDecimal.valueOf(40), BigDecimal.valueOf(25000),
                null, null, BigDecimal.valueOf(50000), null, null,
                null, null, null, null, "edited");
        assertEquals(BigDecimal.valueOf(40), updated.getHoursWorked());
        assertEquals(BigDecimal.valueOf(25000), updated.getHourlyRate());
        assertEquals(BigDecimal.valueOf(50000), updated.getBonus());
        assertEquals("edited", updated.getNotes());
    }

    @Test @DisplayName("update rejects PAID entry") void update_paid_rejected() {
        payroll.setStatus(PayrollEntry.PaymentStatus.PAID);
        when(payrollRepository.findById(1L)).thenReturn(Optional.of(payroll));
        assertThrows(RuntimeException.class, () -> payrollService.updatePayrollEntry(
                1L, BigDecimal.TEN, null, null, null, null, null, null, null, null, null, null, null));
    }
}
