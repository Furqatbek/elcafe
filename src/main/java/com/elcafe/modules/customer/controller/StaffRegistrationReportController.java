package com.elcafe.modules.customer.controller;

import com.elcafe.modules.customer.dto.StaffRegistrationReport;
import com.elcafe.modules.customer.service.StaffRegistrationReportService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * The till-registration report (V182's {@code registered_by_user_id}, finally read).
 *
 * <p><b>Why this is not on {@code CustomerController}.</b> That controller lets waiters and cashiers
 * register guests, and rightly so. This endpoint reports on <em>them</em> — it is oversight data about
 * employees, and a cashier should not be able to read their colleagues' numbers, or watch their own
 * scorecard build mid-shift. So it lives behind its own narrower gate rather than inheriting a wide one,
 * and {@code RbacGateAnnotationTest} pins that it stays narrow.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Staff Reports", description = "Oversight reporting on staff-performed actions")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER')")
public class StaffRegistrationReportController {

    private final StaffRegistrationReportService reportService;

    @GetMapping("/staff-registrations")
    @Operation(summary = "Guests registered at the till, per employee",
            description = "Registrations and SMS reachability per employee over a date range, with a "
                    + "daily breakdown and the welcome bonus credited through each. Defaults to the "
                    + "last 30 days; the window is capped at 92. Owner/manager only.")
    public ResponseEntity<ApiResponse<StaffRegistrationReport>> staffRegistrations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(reportService.report(from, to)));
    }
}
