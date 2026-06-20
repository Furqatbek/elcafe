package com.elcafe.modules.pos.shift.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.pos.shift.dto.ShiftFinancialReportDTO;
import com.elcafe.modules.pos.shift.service.ShiftReportService;
import com.elcafe.utils.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/shift-reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class ShiftReportController {

    private final ShiftReportService shiftReportService;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<ApiResponse<ShiftFinancialReportDTO>> getShiftReport(
            @PathVariable Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "25000") BigDecimal hourlyRate) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        ShiftFinancialReportDTO report = shiftReportService.getShiftReport(restaurantId, date, hourlyRate);
        return ResponseEntity.ok(ApiResponse.success("Shift report generated", report));
    }
}
