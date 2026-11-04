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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @Test @DisplayName("void — a PAID entry can be voided, which delete refuses")
    void voidPaidEntry() {
        // Auto-pay approves and pays in one go, so every salary generated for
        // someone who had already left was immediately PAID — and PAID entries
        // cannot be deleted. The error told callers to "use void/reverse
        // instead", which did not exist, so they could not be removed at all.
        payroll.setStatus(PayrollEntry.PaymentStatus.PAID);
        when(payrollRepository.findById(1L)).thenReturn(Optional.of(payroll));
        when(payrollRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PayrollEntry voided = payrollService.voidPayrollEntry(1L, "Paid to a deactivated waiter", "admin");

        assertEquals(PayrollEntry.PaymentStatus.CANCELLED, voided.getStatus());
        assertTrue(voided.isDeleted());
        assertEquals("admin", voided.getDeletedBy());
        assertTrue(voided.getNotes().contains("Paid to a deactivated waiter"));
        assertTrue(voided.getNotes().contains("VOIDED by admin"));
    }

    @Test @DisplayName("void — a reason is required")
    void voidRequiresReason() {
        payroll.setStatus(PayrollEntry.PaymentStatus.PAID);
        when(payrollRepository.findById(1L)).thenReturn(Optional.of(payroll));

        assertThrows(Exception.class, () -> payrollService.voidPayrollEntry(1L, "  ", "admin"));
        verify(payrollRepository, never()).save(any());
    }

    @Test @DisplayName("void — cannot void twice")
    void voidTwiceRejected() {
        payroll.softDelete("admin");
        when(payrollRepository.findById(1L)).thenReturn(Optional.of(payroll));

        assertThrows(Exception.class, () -> payrollService.voidPayrollEntry(1L, "duplicate", "admin"));
    }

    @Test @DisplayName("delete — still refuses a PAID entry, pointing at void")
    void deletePaidStillRefused() {
        payroll.setStatus(PayrollEntry.PaymentStatus.PAID);
        when(payrollRepository.findById(1L)).thenReturn(Optional.of(payroll));

        Exception e = assertThrows(Exception.class, () -> payrollService.deletePayrollEntry(1L, "admin"));
        assertTrue(e.getMessage().contains("void"));
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
