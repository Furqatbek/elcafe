package com.elcafe.modules.waiter.service;

import com.elcafe.modules.financial.repository.PayrollEntryRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.waiter.dto.CommissionConfigRequest;
import com.elcafe.modules.waiter.dto.WaiterCommissionSummaryDTO;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.entity.WaiterCommission;
import com.elcafe.modules.waiter.enums.CommissionStatus;
import com.elcafe.modules.waiter.enums.CommissionType;
import com.elcafe.modules.waiter.repository.WaiterCommissionRepository;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createRestaurant;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createWaiter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WaiterCommissionServiceTest {

    @Mock
    private WaiterCommissionRepository commissionRepository;

    @Mock
    private WaiterRepository waiterRepository;

    @Mock
    private PayrollEntryRepository payrollEntryRepository;

    @InjectMocks
    private WaiterCommissionService commissionService;

    private Order order;
    private Waiter waiter;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = createRestaurant();
        waiter = createWaiter(1L, "Test Waiter", "1234");
        waiter.setCommissionEnabled(true);
        waiter.setCommissionPercent(new BigDecimal("5.00"));
        waiter.setCommissionType(CommissionType.PERCENTAGE);

        order = createOrder(1L, OrderStatus.COMPLETED);
        order.setWaiter(waiter);
        order.setRestaurant(restaurant);
        order.setTotal(new BigDecimal("100000.00"));
    }

    // ==================== calculateCommissionForOrder ====================

    @Nested
    @DisplayName("calculateCommissionForOrder")
    class CalculateCommissionForOrder {

        @Test
        @DisplayName("1. percentage commission calculates correctly (5% of 100000 = 5000)")
        void percentageCommission_calculatesCorrectly() {
            when(commissionRepository.existsByWaiterIdAndOrderId(anyLong(), anyLong()))
                    .thenReturn(false);
            when(commissionRepository.save(any(WaiterCommission.class))).thenAnswer(i -> {
                WaiterCommission c = i.getArgument(0);
                c.setId(1L);
                return c;
            });

            Optional<WaiterCommission> result = commissionService.calculateCommissionForOrder(order);

            assertTrue(result.isPresent());
            WaiterCommission commission = result.get();
            assertEquals(0, new BigDecimal("5000.00").compareTo(commission.getCommissionAmount()));
            assertEquals(0, new BigDecimal("5.00").compareTo(commission.getCommissionPercent()));
            assertEquals(0, new BigDecimal("100000.00").compareTo(commission.getOrderTotal()));
            assertEquals(CommissionStatus.PENDING, commission.getStatus());
            assertEquals(waiter, commission.getWaiter());
            assertEquals(order, commission.getOrder());
            assertEquals(restaurant, commission.getRestaurant());
            verify(commissionRepository).save(any(WaiterCommission.class));
        }

        @Test
        @DisplayName("2. fixed commission uses fixed amount regardless of order total")
        void fixedCommission_usesFixedAmount() {
            waiter.setCommissionType(CommissionType.FIXED_AMOUNT);
            waiter.setFixedCommissionAmount(new BigDecimal("15000.00"));

            when(commissionRepository.existsByWaiterIdAndOrderId(anyLong(), anyLong()))
                    .thenReturn(false);
            when(commissionRepository.save(any(WaiterCommission.class))).thenAnswer(i -> {
                WaiterCommission c = i.getArgument(0);
                c.setId(1L);
                return c;
            });

            Optional<WaiterCommission> result = commissionService.calculateCommissionForOrder(order);

            assertTrue(result.isPresent());
            assertEquals(0, new BigDecimal("15000.00").compareTo(result.get().getCommissionAmount()));
        }

        @Test
        @DisplayName("3. returns empty when order has no waiter assigned")
        void noWaiter_returnsEmpty() {
            order.setWaiter(null);

            Optional<WaiterCommission> result = commissionService.calculateCommissionForOrder(order);

            assertTrue(result.isEmpty());
            verify(commissionRepository, never()).save(any());
        }

        @Test
        @DisplayName("4. returns empty when commission is disabled for waiter")
        void commissionDisabled_returnsEmpty() {
            waiter.setCommissionEnabled(false);

            Optional<WaiterCommission> result = commissionService.calculateCommissionForOrder(order);

            assertTrue(result.isEmpty());
            verify(commissionRepository, never()).save(any());
        }

        @Test
        @DisplayName("5. returns empty when commission percent is zero for percentage type")
        void zeroPercent_returnsEmpty() {
            waiter.setCommissionPercent(BigDecimal.ZERO);

            Optional<WaiterCommission> result = commissionService.calculateCommissionForOrder(order);

            assertTrue(result.isEmpty());
            verify(commissionRepository, never()).save(any());
        }

        @Test
        @DisplayName("6. returns existing commission when already exists for waiter and order")
        void alreadyExists_returnsExisting() {
            WaiterCommission existing = WaiterCommission.builder()
                    .id(99L)
                    .waiter(waiter)
                    .order(order)
                    .restaurant(restaurant)
                    .orderTotal(new BigDecimal("100000.00"))
                    .commissionPercent(new BigDecimal("5.00"))
                    .commissionAmount(new BigDecimal("5000.00"))
                    .status(CommissionStatus.PENDING)
                    .build();

            when(commissionRepository.existsByWaiterIdAndOrderId(anyLong(), anyLong()))
                    .thenReturn(true);
            when(commissionRepository.findByWaiterIdAndOrderId(anyLong(), anyLong()))
                    .thenReturn(Optional.of(existing));

            Optional<WaiterCommission> result = commissionService.calculateCommissionForOrder(order);

            assertTrue(result.isPresent());
            assertEquals(99L, result.get().getId());
            assertEquals(0, new BigDecimal("5000.00").compareTo(result.get().getCommissionAmount()));
            verify(commissionRepository, never()).save(any());
        }
    }

    // ==================== updateCommissionConfig ====================

    @Nested
    @DisplayName("updateCommissionConfig")
    class UpdateCommissionConfig {

        @Test
        @DisplayName("7. updates all commission configuration fields")
        void updatesAllFields() {
            CommissionConfigRequest request = CommissionConfigRequest.builder()
                    .commissionPercent(new BigDecimal("10.00"))
                    .commissionEnabled(true)
                    .commissionType(CommissionType.FIXED_AMOUNT)
                    .fixedCommissionAmount(new BigDecimal("25000.00"))
                    .build();

            when(waiterRepository.findById(1L)).thenReturn(Optional.of(waiter));
            when(waiterRepository.save(any(Waiter.class))).thenAnswer(i -> i.getArgument(0));

            Waiter result = commissionService.updateCommissionConfig(1L, request);

            assertEquals(0, new BigDecimal("10.00").compareTo(result.getCommissionPercent()));
            assertTrue(result.getCommissionEnabled());
            assertEquals(CommissionType.FIXED_AMOUNT, result.getCommissionType());
            assertEquals(0, new BigDecimal("25000.00").compareTo(result.getFixedCommissionAmount()));
            verify(waiterRepository).save(waiter);
        }

        @Test
        @DisplayName("8. defaults to PERCENTAGE type when commissionType is null")
        void defaultsToPercentageType() {
            CommissionConfigRequest request = CommissionConfigRequest.builder()
                    .commissionPercent(new BigDecimal("7.50"))
                    .commissionEnabled(true)
                    .commissionType(null)
                    .fixedCommissionAmount(null)
                    .build();

            when(waiterRepository.findById(1L)).thenReturn(Optional.of(waiter));
            when(waiterRepository.save(any(Waiter.class))).thenAnswer(i -> i.getArgument(0));

            Waiter result = commissionService.updateCommissionConfig(1L, request);

            assertEquals(CommissionType.PERCENTAGE, result.getCommissionType());
            assertEquals(0, BigDecimal.ZERO.compareTo(result.getFixedCommissionAmount()));
        }

        @Test
        @DisplayName("9. throws EntityNotFoundException when waiter not found")
        void waiterNotFound_throws() {
            CommissionConfigRequest request = CommissionConfigRequest.builder()
                    .commissionPercent(new BigDecimal("5.00"))
                    .commissionEnabled(true)
                    .build();

            when(waiterRepository.findById(999L)).thenReturn(Optional.empty());

            EntityNotFoundException ex = assertThrows(EntityNotFoundException.class,
                    () -> commissionService.updateCommissionConfig(999L, request));
            assertTrue(ex.getMessage().contains("Waiter not found: 999"));
            verify(waiterRepository, never()).save(any());
        }
    }

    // ==================== getCommissionSummary ====================

    @Nested
    @DisplayName("getCommissionSummary")
    class GetCommissionSummary {

        @Test
        @DisplayName("10. calculates correct totals from commissions in date range")
        void calculatesCorrectTotals() {
            LocalDate startDate = LocalDate.of(2026, 3, 1);
            LocalDate endDate = LocalDate.of(2026, 3, 31);
            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

            waiter.setFixedCommissionAmount(BigDecimal.ZERO);

            WaiterCommission pending = WaiterCommission.builder()
                    .id(1L).waiter(waiter).order(order).restaurant(restaurant)
                    .orderTotal(new BigDecimal("80000.00"))
                    .commissionPercent(new BigDecimal("5.00"))
                    .commissionAmount(new BigDecimal("4000.00"))
                    .status(CommissionStatus.PENDING)
                    .build();
            pending.setCreatedAt(LocalDateTime.of(2026, 3, 10, 12, 0));

            Order order2 = createOrder(11L, OrderStatus.COMPLETED);
            order2.setWaiter(waiter);
            order2.setRestaurant(restaurant);

            WaiterCommission approved = WaiterCommission.builder()
                    .id(2L).waiter(waiter).order(order2).restaurant(restaurant)
                    .orderTotal(new BigDecimal("120000.00"))
                    .commissionPercent(new BigDecimal("5.00"))
                    .commissionAmount(new BigDecimal("6000.00"))
                    .status(CommissionStatus.APPROVED)
                    .build();
            approved.setCreatedAt(LocalDateTime.of(2026, 3, 15, 14, 30));

            Order order3 = createOrder(12L, OrderStatus.COMPLETED);
            order3.setWaiter(waiter);
            order3.setRestaurant(restaurant);

            WaiterCommission paid = WaiterCommission.builder()
                    .id(3L).waiter(waiter).order(order3).restaurant(restaurant)
                    .orderTotal(new BigDecimal("50000.00"))
                    .commissionPercent(new BigDecimal("5.00"))
                    .commissionAmount(new BigDecimal("2500.00"))
                    .status(CommissionStatus.PAID)
                    .build();
            paid.setCreatedAt(LocalDateTime.of(2026, 3, 15, 18, 0));

            List<WaiterCommission> commissions = List.of(pending, approved, paid);

            when(waiterRepository.findById(1L)).thenReturn(Optional.of(waiter));
            when(commissionRepository.findByWaiterIdAndDateRange(
                    eq(1L), eq(startDateTime), eq(endDateTime))).thenReturn(commissions);

            WaiterCommissionSummaryDTO summary =
                    commissionService.getCommissionSummary(1L, startDate, endDate);

            assertNotNull(summary);
            assertEquals(1L, summary.getWaiterId());
            assertEquals("Test Waiter", summary.getWaiterName());
            assertEquals(3L, summary.getTotalCommissions());
            // 80000 + 120000 + 50000 = 250000
            assertEquals(0, new BigDecimal("250000.00").compareTo(summary.getTotalOrderValue()));
            // 4000 + 6000 + 2500 = 12500
            assertEquals(0, new BigDecimal("12500.00").compareTo(summary.getTotalCommissionEarned()));
            assertEquals(0, new BigDecimal("4000.00").compareTo(summary.getPendingCommission()));
            // APPROVED status maps to approvedCommission
            assertEquals(0, new BigDecimal("6000.00").compareTo(summary.getApprovedCommission()));
            assertEquals(0, new BigDecimal("2500.00").compareTo(summary.getPaidCommission()));
            assertEquals(startDate, summary.getPeriodStart());
            assertEquals(endDate, summary.getPeriodEnd());
            assertTrue(summary.getCommissionEnabled());
            assertEquals(CommissionType.PERCENTAGE, summary.getCommissionType());

            // Daily breakdown: 2 distinct dates (March 10, March 15)
            assertNotNull(summary.getDailyCommissions());
            assertEquals(2, summary.getDailyCommissions().size());

            WaiterCommissionSummaryDTO.DailyCommission day1 =
                    summary.getDailyCommissions().get(0);
            assertEquals(LocalDate.of(2026, 3, 10), day1.getDate());
            assertEquals(1L, day1.getOrderCount());
            assertEquals(0, new BigDecimal("4000.00").compareTo(day1.getCommissionEarned()));

            WaiterCommissionSummaryDTO.DailyCommission day2 =
                    summary.getDailyCommissions().get(1);
            assertEquals(LocalDate.of(2026, 3, 15), day2.getDate());
            assertEquals(2L, day2.getOrderCount());
            // 6000 + 2500 = 8500
            assertEquals(0, new BigDecimal("8500.00").compareTo(day2.getCommissionEarned()));
        }
    }

    // ==================== approveCommissions ====================

    @Nested
    @DisplayName("approveCommissions")
    class ApproveCommissions {

        @Test
        @DisplayName("11. approves pending commissions and sets status to APPROVED")
        void approvesPendingCommissions() {
            WaiterCommission c1 = WaiterCommission.builder()
                    .id(1L).waiter(waiter).order(order).restaurant(restaurant)
                    .orderTotal(new BigDecimal("100000.00"))
                    .commissionPercent(new BigDecimal("5.00"))
                    .commissionAmount(new BigDecimal("5000.00"))
                    .status(CommissionStatus.PENDING)
                    .build();

            WaiterCommission c2 = WaiterCommission.builder()
                    .id(2L).waiter(waiter).order(order).restaurant(restaurant)
                    .orderTotal(new BigDecimal("80000.00"))
                    .commissionPercent(new BigDecimal("5.00"))
                    .commissionAmount(new BigDecimal("4000.00"))
                    .status(CommissionStatus.PENDING)
                    .build();

            when(commissionRepository.findById(1L)).thenReturn(Optional.of(c1));
            when(commissionRepository.findById(2L)).thenReturn(Optional.of(c2));
            when(commissionRepository.save(any(WaiterCommission.class)))
                    .thenAnswer(i -> i.getArgument(0));

            List<WaiterCommission> result =
                    commissionService.approveCommissions(List.of(1L, 2L));

            assertEquals(2, result.size());
            assertEquals(CommissionStatus.APPROVED, result.get(0).getStatus());
            assertEquals(CommissionStatus.APPROVED, result.get(1).getStatus());
        }

        @Test
        @DisplayName("12. skips commissions that are not in PENDING status")
        void skipsNonPending() {
            WaiterCommission pendingC = WaiterCommission.builder()
                    .id(1L).waiter(waiter).order(order).restaurant(restaurant)
                    .orderTotal(new BigDecimal("100000.00"))
                    .commissionPercent(new BigDecimal("5.00"))
                    .commissionAmount(new BigDecimal("5000.00"))
                    .status(CommissionStatus.PENDING)
                    .build();

            WaiterCommission approvedC = WaiterCommission.builder()
                    .id(2L).waiter(waiter).order(order).restaurant(restaurant)
                    .orderTotal(new BigDecimal("80000.00"))
                    .commissionPercent(new BigDecimal("5.00"))
                    .commissionAmount(new BigDecimal("4000.00"))
                    .status(CommissionStatus.APPROVED)
                    .build();

            WaiterCommission paidC = WaiterCommission.builder()
                    .id(3L).waiter(waiter).order(order).restaurant(restaurant)
                    .orderTotal(new BigDecimal("60000.00"))
                    .commissionPercent(new BigDecimal("5.00"))
                    .commissionAmount(new BigDecimal("3000.00"))
                    .status(CommissionStatus.PAID)
                    .build();

            when(commissionRepository.findById(1L)).thenReturn(Optional.of(pendingC));
            when(commissionRepository.findById(2L)).thenReturn(Optional.of(approvedC));
            when(commissionRepository.findById(3L)).thenReturn(Optional.of(paidC));
            when(commissionRepository.save(any(WaiterCommission.class)))
                    .thenAnswer(i -> i.getArgument(0));

            List<WaiterCommission> result =
                    commissionService.approveCommissions(List.of(1L, 2L, 3L));

            // Only the PENDING one should be approved and returned
            assertEquals(1, result.size());
            assertEquals(1L, result.get(0).getId());
            assertEquals(CommissionStatus.APPROVED, result.get(0).getStatus());
        }
    }

    // ==================== cancelCommission ====================

    @Nested
    @DisplayName("cancelCommission")
    class CancelCommission {

        @Test
        @DisplayName("13. cancels all commissions for a given order")
        void cancelsForOrder() {
            WaiterCommission commission = WaiterCommission.builder()
                    .id(1L).waiter(waiter).order(order).restaurant(restaurant)
                    .orderTotal(new BigDecimal("100000.00"))
                    .commissionPercent(new BigDecimal("5.00"))
                    .commissionAmount(new BigDecimal("5000.00"))
                    .status(CommissionStatus.PENDING)
                    .build();

            when(commissionRepository.findByOrderId(1L)).thenReturn(List.of(commission));
            when(commissionRepository.save(any(WaiterCommission.class)))
                    .thenAnswer(i -> i.getArgument(0));

            commissionService.cancelCommission(1L);

            ArgumentCaptor<WaiterCommission> captor =
                    ArgumentCaptor.forClass(WaiterCommission.class);
            verify(commissionRepository).save(captor.capture());
            assertEquals(CommissionStatus.CANCELLED, captor.getValue().getStatus());
        }
    }

    // ==================== getRestaurantCommissionReport ====================

    @Nested
    @DisplayName("getRestaurantCommissionReport")
    class GetRestaurantCommissionReport {

        @Test
        @DisplayName("14. filters to active waiters with commission enabled and non-zero commissions")
        void filtersActiveCommissionEnabled() {
            LocalDate startDate = LocalDate.of(2026, 3, 1);
            LocalDate endDate = LocalDate.of(2026, 3, 31);
            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.atTime(LocalTime.MAX);

            Waiter enabledWaiter = createWaiter(1L, "Enabled Waiter", "1111");
            enabledWaiter.setCommissionEnabled(true);
            enabledWaiter.setCommissionPercent(new BigDecimal("5.00"));
            enabledWaiter.setCommissionType(CommissionType.PERCENTAGE);
            enabledWaiter.setFixedCommissionAmount(BigDecimal.ZERO);

            Waiter disabledWaiter = createWaiter(2L, "Disabled Waiter", "2222");
            disabledWaiter.setCommissionEnabled(false);

            Waiter noCommissionsWaiter = createWaiter(3L, "No Commissions", "3333");
            noCommissionsWaiter.setCommissionEnabled(true);
            noCommissionsWaiter.setCommissionPercent(new BigDecimal("3.00"));
            noCommissionsWaiter.setCommissionType(CommissionType.PERCENTAGE);
            noCommissionsWaiter.setFixedCommissionAmount(BigDecimal.ZERO);

            when(waiterRepository.findByActiveTrueOrderByNameAsc())
                    .thenReturn(List.of(disabledWaiter, enabledWaiter, noCommissionsWaiter));

            // Enabled waiter has commissions
            WaiterCommission commission = WaiterCommission.builder()
                    .id(1L).waiter(enabledWaiter).order(order).restaurant(restaurant)
                    .orderTotal(new BigDecimal("100000.00"))
                    .commissionPercent(new BigDecimal("5.00"))
                    .commissionAmount(new BigDecimal("5000.00"))
                    .status(CommissionStatus.PENDING)
                    .build();
            commission.setCreatedAt(LocalDateTime.of(2026, 3, 15, 10, 0));

            when(waiterRepository.findById(1L)).thenReturn(Optional.of(enabledWaiter));
            when(commissionRepository.findByWaiterIdAndDateRange(
                    eq(1L), eq(startDateTime), eq(endDateTime)))
                    .thenReturn(List.of(commission));

            // No-commissions waiter returns empty
            when(waiterRepository.findById(3L)).thenReturn(Optional.of(noCommissionsWaiter));
            when(commissionRepository.findByWaiterIdAndDateRange(
                    eq(3L), eq(startDateTime), eq(endDateTime)))
                    .thenReturn(List.of());

            List<WaiterCommissionSummaryDTO> report =
                    commissionService.getRestaurantCommissionReport(1L, startDate, endDate);

            // disabledWaiter filtered by commissionEnabled check
            // noCommissionsWaiter filtered by totalCommissions > 0
            assertEquals(1, report.size());
            assertEquals(1L, report.get(0).getWaiterId());
            assertEquals("Enabled Waiter", report.get(0).getWaiterName());
            assertEquals(1L, report.get(0).getTotalCommissions());
            assertEquals(0,
                    new BigDecimal("5000.00").compareTo(report.get(0).getTotalCommissionEarned()));
        }
    }
}
