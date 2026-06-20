package com.elcafe.modules.inventory.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.inventory.dto.WasteRecordRequest;
import com.elcafe.modules.inventory.dto.WasteRecordResponse;
import com.elcafe.modules.inventory.dto.WasteReportResponse;
import com.elcafe.modules.inventory.entity.WasteRecord;
import com.elcafe.modules.inventory.service.WasteService;
import com.elcafe.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/inventory/waste")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER', 'OPERATOR')")
public class WasteController {

    private final WasteService wasteService;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<WasteRecordResponse>>> getWasteRecords(
            @RequestParam Long restaurantId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) WasteRecord.WasteReason reason,
            @RequestParam(required = false) Long ingredientId) {
        restaurantAuthorizationService.checkAccess(restaurantId);

        log.info("Fetching waste records for restaurant: {}", restaurantId);

        List<WasteRecord> records;
        if (startDate != null && endDate != null) {
            records = wasteService.getWasteRecords(restaurantId, startDate, endDate, reason, ingredientId);
        } else {
            records = wasteService.getWasteRecords(restaurantId);
        }

        List<WasteRecordResponse> responses = records.stream()
                .map(WasteRecordResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Waste records retrieved successfully", responses));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WasteRecordResponse>> getWasteRecordById(@PathVariable Long id) {
        log.info("Fetching waste record: {}", id);

        WasteRecord record = wasteService.getWasteRecordById(id);
        WasteRecordResponse response = WasteRecordResponse.fromEntity(record);

        return ResponseEntity.ok(ApiResponse.success("Waste record retrieved successfully", response));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WasteRecordResponse>> recordWaste(
            @Valid @RequestBody WasteRecordRequest request) {
        restaurantAuthorizationService.checkAccess(request.getRestaurantId());
        log.info("Recording waste for ingredient: {} in restaurant: {}",
                request.getIngredientId(), request.getRestaurantId());

        WasteRecord record = wasteService.recordWaste(request);
        WasteRecordResponse response = WasteRecordResponse.fromEntity(record);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Waste recorded successfully", response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteWasteRecord(@PathVariable Long id) {
        log.info("Deleting waste record: {}", id);

        wasteService.deleteWasteRecord(id);

        return ResponseEntity.ok(ApiResponse.success("Waste record deleted successfully", null));
    }

    @GetMapping("/report")
    public ResponseEntity<ApiResponse<WasteReportResponse>> getWasteReport(
            @RequestParam Long restaurantId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        restaurantAuthorizationService.checkAccess(restaurantId);

        log.info("Generating waste report for restaurant: {} from {} to {}", restaurantId, startDate, endDate);

        WasteReportResponse report = wasteService.generateWasteReport(restaurantId, startDate, endDate);

        return ResponseEntity.ok(ApiResponse.success("Waste report generated successfully", report));
    }

    @GetMapping("/reasons")
    public ResponseEntity<ApiResponse<List<ReasonOption>>> getWasteReasons() {
        List<ReasonOption> reasons = Arrays.stream(WasteRecord.WasteReason.values())
                .map(r -> new ReasonOption(r.name(), r.getLabel(), r.getDescription()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Waste reasons retrieved successfully", reasons));
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ReasonOption {
        private String value;
        private String label;
        private String description;
    }
}
