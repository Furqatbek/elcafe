package com.elcafe.modules.pos.shift.controller;

import com.elcafe.modules.pos.shift.dto.ShiftHandoverDTO;
import com.elcafe.modules.pos.shift.service.ShiftHandoverService;
import com.elcafe.utils.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Slf4j
@RestController
@RequestMapping("/api/v1/shift-handover")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class ShiftHandoverController {

    private final ShiftHandoverService handoverService;

    @GetMapping("/{shiftId}/prepare")
    public ResponseEntity<ApiResponse<ShiftHandoverDTO>> prepareHandover(@PathVariable Long shiftId) {
        log.info("Preparing handover for shift {}", shiftId);
        ShiftHandoverDTO dto = handoverService.prepareHandover(shiftId);
        return ResponseEntity.ok(ApiResponse.success("Handover prepared", dto));
    }

    @PostMapping("/{shiftId}/complete")
    public ResponseEntity<ApiResponse<ShiftHandoverDTO>> completeHandover(
            @PathVariable Long shiftId,
            @RequestBody CompleteHandoverRequest request) {
        log.info("Completing handover for shift {}", shiftId);
        ShiftHandoverDTO dto = handoverService.completeHandover(shiftId, request.countedCash, request.notes);
        return ResponseEntity.ok(ApiResponse.success("Handover completed", dto));
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CompleteHandoverRequest {
        private BigDecimal countedCash;
        private String notes;
    }
}
