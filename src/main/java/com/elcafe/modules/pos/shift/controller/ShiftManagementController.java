package com.elcafe.modules.pos.shift.controller;

import com.elcafe.modules.pos.shift.dto.*;
import com.elcafe.modules.pos.shift.entity.EmployeeShift;
import com.elcafe.modules.pos.shift.entity.ShiftBreak;
import com.elcafe.modules.pos.shift.enums.BreakType;
import com.elcafe.modules.pos.shift.service.ShiftManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/pos/shifts")
@RequiredArgsConstructor
@Tag(name = "Shift Management", description = "Employee shift and clock-in/out management")
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER', 'OPERATOR', 'WAITER')")
public class ShiftManagementController {

    private final ShiftManagementService shiftService;

    @PostMapping("/clock-in")
    @Operation(summary = "Clock in an employee to start shift")
    public ResponseEntity<EmployeeShift> clockIn(
            @PathVariable Long restaurantId,
            @Valid @RequestBody ClockInRequest request) {
        return ResponseEntity.ok(shiftService.clockIn(restaurantId, request));
    }

    @PostMapping("/{shiftId}/clock-out")
    @Operation(summary = "Clock out an employee to end shift")
    public ResponseEntity<EmployeeShift> clockOut(
            @PathVariable Long restaurantId,
            @PathVariable Long shiftId,
            @Valid @RequestBody ClockOutRequest request) {
        return ResponseEntity.ok(shiftService.clockOut(shiftId, request));
    }

    @PostMapping("/{shiftId}/break/start")
    @Operation(summary = "Start a break")
    public ResponseEntity<ShiftBreak> startBreak(
            @PathVariable Long restaurantId,
            @PathVariable Long shiftId,
            @RequestParam(required = false) BreakType breakType) {
        return ResponseEntity.ok(shiftService.startBreak(shiftId, breakType));
    }

    @PostMapping("/{shiftId}/break/end")
    @Operation(summary = "End a break")
    public ResponseEntity<ShiftBreak> endBreak(
            @PathVariable Long restaurantId,
            @PathVariable Long shiftId) {
        return ResponseEntity.ok(shiftService.endBreak(shiftId));
    }

    @GetMapping("/active")
    @Operation(summary = "Get all active shifts")
    public ResponseEntity<List<ShiftSummaryDTO>> getActiveShifts(
            @PathVariable Long restaurantId) {
        return ResponseEntity.ok(shiftService.getActiveShifts(restaurantId));
    }

    @GetMapping("/date/{date}")
    @Operation(summary = "Get shifts for a specific date")
    public ResponseEntity<List<ShiftSummaryDTO>> getShiftsByDate(
            @PathVariable Long restaurantId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(shiftService.getShiftsByDate(restaurantId, date));
    }

    @GetMapping("/pending-approval")
    @Operation(summary = "Get shifts pending manager approval")
    public ResponseEntity<List<ShiftSummaryDTO>> getPendingApproval(
            @PathVariable Long restaurantId) {
        return ResponseEntity.ok(shiftService.getPendingApprovalShifts(restaurantId));
    }

    @PostMapping("/{shiftId}/approve")
    @Operation(summary = "Approve a completed shift")
    public ResponseEntity<EmployeeShift> approveShift(
            @PathVariable Long restaurantId,
            @PathVariable Long shiftId,
            @RequestParam Long managerId,
            @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(shiftService.approveShift(shiftId, managerId, notes));
    }

    @GetMapping("/employees/{employeeId}/history")
    @Operation(summary = "Get shift history for an employee")
    public ResponseEntity<Page<ShiftSummaryDTO>> getEmployeeHistory(
            @PathVariable Long restaurantId,
            @PathVariable Long employeeId,
            Pageable pageable) {
        return ResponseEntity.ok(shiftService.getEmployeeShiftHistory(employeeId, pageable));
    }

    @GetMapping("/end-of-day/{date}")
    @Operation(summary = "Get end-of-day report")
    public ResponseEntity<EndOfDayReport> getEndOfDayReport(
            @PathVariable Long restaurantId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(shiftService.getEndOfDayReport(restaurantId, date));
    }
}
