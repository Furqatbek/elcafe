package com.elcafe.modules.waiter.service;

import com.elcafe.modules.financial.repository.PayrollEntryRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.entity.WaiterCommission;
import com.elcafe.modules.waiter.enums.CommissionStatus;
import com.elcafe.modules.waiter.enums.CommissionType;
import com.elcafe.modules.waiter.dto.CommissionConfigRequest;
import com.elcafe.modules.waiter.repository.WaiterCommissionRepository;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaiterCommissionServiceTest {

    @Mock private WaiterCommissionRepository commissionRepository;
    @Mock private WaiterRepository waiterRepository;
    @Mock private PayrollEntryRepository payrollEntryRepository;

    @InjectMocks private WaiterCommissionService commissionService;

    private Order order;
    private Waiter waiter;

    @BeforeEach
    void setUp() {
        waiter = createWaiter();
        waiter.setCommissionEnabled(true);
        waiter.setCommissionPercent(BigDecimal.valueOf(5));
        waiter.setCommissionType(CommissionType.PERCENTAGE);

        order = createOrder(1L, OrderStatus.COMPLETED);
        order.setWaiter(waiter);
        order.setTotal(BigDecimal.valueOf(100000));
    }

    // ==================== calculateCommissionForOrder ====================

    @Test
    void percentageCommission_calculatesCorrectly() {
        when(commissionRepository.existsByWaiterIdAndOrderId(anyLong(), anyLong())).thenReturn(false);
        when(commissionRepository.save(any(WaiterCommission.class))).thenAnswer(i -> {
            WaiterCommission c = i.getArgument(0); c.setId(1L); return c;
        });

        Optional<WaiterCommission> result = commissionService.calculateCommissionForOrder(order);

        assertTrue(result.isPresent());
        // 5% of 100000 = 5000
        assertEquals(0, BigDecimal.valueOf(5000).compareTo(result.get().getCommissionAmount()));
    }

    @Test
    void fixedCommission_usesFixedAmount() {
        waiter.setCommissionType(CommissionType.FIXED_AMOUNT);
        waiter.setFixedCommissionAmount(BigDecimal.valueOf(10000));

        when(commissionRepository.existsByWaiterIdAndOrderId(anyLong(), anyLong())).thenReturn(false);
        when(commissionRepository.save(any(WaiterCommission.class))).thenAnswer(i -> {
            WaiterCommission c = i.getArgument(0); c.setId(1L); return c;
        });

        Optional<WaiterCommission> result = commissionService.calculateCommissionForOrder(order);

        assertTrue(result.isPresent());
        assertEquals(0, BigDecimal.valueOf(10000).compareTo(result.get().getCommissionAmount()));
    }

    @Test
    void noWaiter_returnsEmpty() {
        order.setWaiter(null);
        Optional<WaiterCommission> result = commissionService.calculateCommissionForOrder(order);
        assertTrue(result.isEmpty());
    }

    @Test
    void commissionDisabled_returnsEmpty() {
        waiter.setCommissionEnabled(false);
        Optional<WaiterCommission> result = commissionService.calculateCommissionForOrder(order);
        assertTrue(result.isEmpty());
    }

    @Test
    void zeroPercent_returnsEmpty() {
        waiter.setCommissionPercent(BigDecimal.ZERO);
        Optional<WaiterCommission> result = commissionService.calculateCommissionForOrder(order);
        assertTrue(result.isEmpty());
    }

    @Test
    void alreadyExists_returnsExisting() {
        WaiterCommission existing = WaiterCommission.builder()
                .id(99L).commissionAmount(BigDecimal.valueOf(5000)).build();
        when(commissionRepository.existsByWaiterIdAndOrderId(anyLong(), anyLong())).thenReturn(true);
        when(commissionRepository.findByWaiterIdAndOrderId(anyLong(), anyLong()))
                .thenReturn(Optional.of(existing));

        Optional<WaiterCommission> result = commissionService.calculateCommissionForOrder(order);

        assertTrue(result.isPresent());
        assertEquals(99L, result.get().getId());
        verify(commissionRepository, never()).save(any());
    }

    @Test
    void nullCommissionType_defaultsToPercentage() {
        waiter.setCommissionType(null); // defaults to PERCENTAGE in service
        when(commissionRepository.existsByWaiterIdAndOrderId(anyLong(), anyLong())).thenReturn(false);
        when(commissionRepository.save(any(WaiterCommission.class))).thenAnswer(i -> {
            WaiterCommission c = i.getArgument(0); c.setId(1L); return c;
        });

        Optional<WaiterCommission> result = commissionService.calculateCommissionForOrder(order);

        assertTrue(result.isPresent());
        // 5% of 100000 = 5000 (percentage calculation used)
        assertEquals(0, BigDecimal.valueOf(5000).compareTo(result.get().getCommissionAmount()));
    }

    // ==================== updateCommissionConfig ====================

    @Test
    void updateConfig_updatesAllFields() {
        CommissionConfigRequest request = new CommissionConfigRequest();
        request.setCommissionEnabled(true);
        request.setCommissionPercent(BigDecimal.valueOf(10));
        request.setCommissionType(CommissionType.PERCENTAGE);

        when(waiterRepository.findById(1L)).thenReturn(Optional.of(waiter));
        when(waiterRepository.save(any(Waiter.class))).thenAnswer(i -> i.getArgument(0));

        Waiter result = commissionService.updateCommissionConfig(1L, request);

        assertTrue(result.getCommissionEnabled());
        assertEquals(0, BigDecimal.valueOf(10).compareTo(result.getCommissionPercent()));
    }

    @Test
    void updateConfig_waiterNotFound_throws() {
        when(waiterRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(EntityNotFoundException.class,
                () -> commissionService.updateCommissionConfig(99L, new CommissionConfigRequest()));
    }

    // ==================== Commission status checks ====================

    @Test
    void commissionSaved_withPendingStatus() {
        when(commissionRepository.existsByWaiterIdAndOrderId(anyLong(), anyLong())).thenReturn(false);
        when(commissionRepository.save(any(WaiterCommission.class))).thenAnswer(i -> {
            WaiterCommission c = i.getArgument(0); c.setId(1L); return c;
        });

        Optional<WaiterCommission> result = commissionService.calculateCommissionForOrder(order);

        assertTrue(result.isPresent());
        ArgumentCaptor<WaiterCommission> captor = ArgumentCaptor.forClass(WaiterCommission.class);
        verify(commissionRepository).save(captor.capture());
        assertEquals(CommissionStatus.PENDING, captor.getValue().getStatus());
    }

    @Test
    void commissionSaved_withCorrectOrderAndWaiterReference() {
        when(commissionRepository.existsByWaiterIdAndOrderId(anyLong(), anyLong())).thenReturn(false);
        when(commissionRepository.save(any(WaiterCommission.class))).thenAnswer(i -> {
            WaiterCommission c = i.getArgument(0); c.setId(1L); return c;
        });

        commissionService.calculateCommissionForOrder(order);

        ArgumentCaptor<WaiterCommission> captor = ArgumentCaptor.forClass(WaiterCommission.class);
        verify(commissionRepository).save(captor.capture());
        assertEquals(waiter, captor.getValue().getWaiter());
        assertEquals(order, captor.getValue().getOrder());
        assertEquals(0, BigDecimal.valueOf(100000).compareTo(captor.getValue().getOrderTotal()));
    }

    @Test
    void fixedAmount_nullAmount_returnsEmpty() {
        waiter.setCommissionType(CommissionType.FIXED_AMOUNT);
        waiter.setFixedCommissionAmount(null);

        Optional<WaiterCommission> result = commissionService.calculateCommissionForOrder(order);
        assertTrue(result.isEmpty());
    }
}
