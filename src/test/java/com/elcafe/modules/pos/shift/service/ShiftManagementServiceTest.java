package com.elcafe.modules.pos.shift.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.pos.cashdrawer.repository.CashDrawerRepository;
import com.elcafe.modules.pos.shift.dto.*;
import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.entity.ShiftBreak;
import com.elcafe.modules.pos.shift.enums.BreakType;
import com.elcafe.modules.pos.shift.enums.ShiftStatus;
import com.elcafe.modules.pos.shift.repository.EmployeeShiftRepository;
import com.elcafe.modules.pos.shift.repository.ShiftBreakRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShiftManagementServiceTest {

    @Mock private EmployeeShiftRepository shiftRepository;
    @Mock private ShiftBreakRepository breakRepository;
    @Mock private UserRepository userRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private WaiterRepository waiterRepository;
    @Mock private CashDrawerRepository cashDrawerRepository;
    @InjectMocks private ShiftManagementService shiftManagementService;

    private Restaurant restaurant;
    private User employee;
    private User manager;
    private EmployeeShift shift;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test");

        employee = new User();
        employee.setId(1L);
        employee.setEmail("cashier@test.com");
        employee.setFirstName("Test");
        employee.setLastName("Cashier");

        manager = new User();
        manager.setId(2L);
        manager.setEmail("manager@test.com");
        manager.setFirstName("Test");
        manager.setLastName("Manager");

        shift = EmployeeShift.builder()
                .id(1L).restaurant(restaurant).employee(employee)
                .shiftDate(LocalDate.now())
                .clockIn(OffsetDateTime.now(ZoneOffset.UTC))
                .status(ShiftStatus.ACTIVE)
                .totalSales(BigDecimal.ZERO).totalCashSales(BigDecimal.ZERO)
                .totalCardSales(BigDecimal.ZERO).totalTips(BigDecimal.ZERO)
                .totalRefunds(BigDecimal.ZERO).totalVoids(BigDecimal.ZERO)
                .totalOrders(0).breakMinutes(0)
                .openingCash(new BigDecimal("500000"))
                .build();
    }

    @Test @DisplayName("clockIn — creates shift with start time")
    void clockIn_success() {
        ClockInRequest request = new ClockInRequest();
        request.setEmployeeId(1L);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(userRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(shiftRepository.findActiveShiftByEmployee(1L)).thenReturn(Optional.empty());
        when(shiftRepository.save(any())).thenAnswer(i -> { EmployeeShift s = i.getArgument(0); s.setId(1L); return s; });

        EmployeeShift result = shiftManagementService.clockIn(1L, request);

        assertThat(result.getStatus()).isEqualTo(ShiftStatus.ACTIVE);
        assertThat(result.getClockIn()).isNotNull();
    }

    @Test @DisplayName("clockIn — already clocked in throws")
    void clockIn_alreadyClockedIn_throws() {
        ClockInRequest request = new ClockInRequest();
        request.setEmployeeId(1L);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(userRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(shiftRepository.findActiveShiftByEmployee(1L)).thenReturn(Optional.of(shift));

        assertThatThrownBy(() -> shiftManagementService.clockIn(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already has an active shift");
    }

    @Test @DisplayName("clockOut — sets end time")
    void clockOut_success() {
        ClockOutRequest request = new ClockOutRequest();
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(shift));
        when(breakRepository.findActiveBreak(1L)).thenReturn(Optional.empty());
        when(shiftRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        EmployeeShift result = shiftManagementService.clockOut(1L, request);

        assertThat(result.getStatus()).isEqualTo(ShiftStatus.COMPLETED);
    }

    @Test @DisplayName("clockOut — not active throws")
    void clockOut_notClockedIn_throws() {
        shift.setStatus(ShiftStatus.COMPLETED);
        ClockOutRequest request = new ClockOutRequest();
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(shift));

        assertThatThrownBy(() -> shiftManagementService.clockOut(1L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not active");
    }

    @Test @DisplayName("startBreak — records break start")
    void startBreak_success() {
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(shift));
        when(breakRepository.findActiveBreak(1L)).thenReturn(Optional.empty());
        when(shiftRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(breakRepository.save(any())).thenAnswer(i -> { ShiftBreak b = i.getArgument(0); b.setId(1L); return b; });

        ShiftBreak result = shiftManagementService.startBreak(1L, BreakType.MEAL);

        assertThat(result.getBreakType()).isEqualTo(BreakType.MEAL);
        assertThat(shift.getStatus()).isEqualTo(ShiftStatus.ON_BREAK);
    }

    @Test @DisplayName("endBreak — records break end")
    void endBreak_success() {
        shift.setStatus(ShiftStatus.ON_BREAK);
        ShiftBreak activeBreak = ShiftBreak.builder()
                .id(1L).shift(shift).breakStart(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(15))
                .breakType(BreakType.MEAL).build();
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(shift));
        when(breakRepository.findActiveBreak(1L)).thenReturn(Optional.of(activeBreak));
        when(shiftRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(breakRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ShiftBreak result = shiftManagementService.endBreak(1L);

        assertThat(result.getBreakEnd()).isNotNull();
        assertThat(shift.getStatus()).isEqualTo(ShiftStatus.ACTIVE);
    }

    @Test @DisplayName("getActiveShifts — returns active list")
    void getActiveShifts_returnsList() {
        when(shiftRepository.findActiveShiftsByRestaurant(1L)).thenReturn(List.of(shift));
        List<ShiftSummaryDTO> result = shiftManagementService.getActiveShifts(1L);
        assertThat(result).hasSize(1);
    }

    @Test @DisplayName("getShiftsByDate — returns by date")
    void getShiftHistory_returnsList() {
        when(shiftRepository.findByRestaurantIdAndShiftDate(1L, LocalDate.now())).thenReturn(List.of(shift));
        List<ShiftSummaryDTO> result = shiftManagementService.getShiftsByDate(1L, LocalDate.now());
        assertThat(result).hasSize(1);
    }

    @Test @DisplayName("getEndOfDayReport — aggregates totals")
    void getEndOfDayReport_aggregates() {
        shift.setTotalSales(new BigDecimal("500000"));
        shift.setTotalCashSales(new BigDecimal("300000"));
        shift.setTotalCardSales(new BigDecimal("200000"));
        shift.setTotalTips(new BigDecimal("50000"));
        shift.setTotalOrders(15);
        when(shiftRepository.findByRestaurantIdAndShiftDate(1L, LocalDate.now())).thenReturn(List.of(shift));

        EndOfDayReport report = shiftManagementService.getEndOfDayReport(1L, LocalDate.now());

        assertThat(report.getTotalShifts()).isEqualTo(1);
        assertThat(report.getTotalSales()).isEqualByComparingTo("500000");
        assertThat(report.getTotalOrders()).isEqualTo(15);
    }

    @Test @DisplayName("approveShift — sets approval")
    void approveShift() {
        shift.setStatus(ShiftStatus.COMPLETED);
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(shift));
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(shiftRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        EmployeeShift result = shiftManagementService.approveShift(1L, 2L, "Looks good");

        assertThat(result.getStatus()).isEqualTo(ShiftStatus.APPROVED);
    }

    @Test @DisplayName("getEmployeeShiftHistory — paginated")
    void getEmployeeShifts_paginated() {
        when(shiftRepository.findByEmployeeIdOrderByShiftDateDesc(1L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(shift), PageRequest.of(0, 20), 1));

        var result = shiftManagementService.getEmployeeShiftHistory(1L, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test @DisplayName("getPendingApprovalShifts — returns pending list")
    void getPendingApprovalShifts_returnsList() {
        shift.setStatus(ShiftStatus.COMPLETED);
        when(shiftRepository.findPendingApproval(1L)).thenReturn(List.of(shift));

        List<ShiftSummaryDTO> result = shiftManagementService.getPendingApprovalShifts(1L);

        assertThat(result).hasSize(1);
    }

    @Test @DisplayName("updateShiftTotals — increments totals")
    void updateShiftTotals_incrementsTotals() {
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(shift));
        when(shiftRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        shiftManagementService.updateShiftTotals(1L, new BigDecimal("100000"),
                new BigDecimal("60000"), new BigDecimal("40000"), new BigDecimal("10000"));

        assertThat(shift.getTotalSales()).isEqualByComparingTo("100000");
        assertThat(shift.getTotalCashSales()).isEqualByComparingTo("60000");
        assertThat(shift.getTotalCardSales()).isEqualByComparingTo("40000");
        assertThat(shift.getTotalTips()).isEqualByComparingTo("10000");
        assertThat(shift.getTotalOrders()).isEqualTo(1);
        verify(shiftRepository).save(shift);
    }

    @Test @DisplayName("recordRefund — increments totalRefunds")
    void recordRefund_incrementsRefunds() {
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(shift));
        when(shiftRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        shiftManagementService.recordRefund(1L, new BigDecimal("25000"));

        assertThat(shift.getTotalRefunds()).isEqualByComparingTo("25000");
        verify(shiftRepository).save(shift);
    }

    @Test @DisplayName("recordVoid — increments totalVoids")
    void recordVoid_incrementsVoids() {
        when(shiftRepository.findById(1L)).thenReturn(Optional.of(shift));
        when(shiftRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        shiftManagementService.recordVoid(1L, new BigDecimal("15000"));

        assertThat(shift.getTotalVoids()).isEqualByComparingTo("15000");
        verify(shiftRepository).save(shift);
    }
}
