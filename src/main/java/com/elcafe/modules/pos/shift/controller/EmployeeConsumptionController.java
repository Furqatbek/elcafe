package com.elcafe.modules.pos.shift.controller;

import com.elcafe.modules.pos.shift.entity.EmployeeConsumption;
import com.elcafe.modules.pos.shift.service.EmployeeConsumptionService;
import com.elcafe.utils.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/employee-consumptions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'WAITER')")
public class EmployeeConsumptionController {

    private final EmployeeConsumptionService consumptionService;

    @PostMapping
    public ResponseEntity<ApiResponse<EmployeeConsumption>> record(
            @PathVariable Long restaurantId,
            @RequestBody RecordRequest request) {
        EmployeeConsumption consumption = consumptionService.recordConsumption(
                restaurantId, request.productId(), request.quantity(),
                request.waiterId(), request.employeeId(), request.notes());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Consumption recorded", consumption));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<EmployeeConsumption>>> getByDateRange(
            @PathVariable Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Consumptions retrieved",
                consumptionService.getByRestaurantAndDateRange(restaurantId, from, to)));
    }

    @GetMapping("/shift/{shiftId}")
    public ResponseEntity<ApiResponse<List<EmployeeConsumption>>> getByShift(
            @PathVariable Long restaurantId,
            @PathVariable Long shiftId) {
        return ResponseEntity.ok(ApiResponse.success("Shift consumptions retrieved",
                consumptionService.getByShift(shiftId)));
    }

    public record RecordRequest(
            Long productId,
            int quantity,
            Long waiterId,
            Long employeeId,
            String notes
    ) {}
}
