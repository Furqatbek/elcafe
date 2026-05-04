package com.elcafe.modules.pos.shift.controller;

import com.elcafe.modules.pos.shift.entity.ShiftSwapRequest;
import com.elcafe.modules.pos.shift.service.ShiftSwapService;
import com.elcafe.utils.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/shift-swaps")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class ShiftSwapController {

    private final ShiftSwapService swapService;

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<ApiResponse<List<ShiftSwapRequest>>> getAll(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(ApiResponse.success("Swap requests retrieved",
                swapService.getAllRequests(restaurantId)));
    }

    @GetMapping("/restaurant/{restaurantId}/pending")
    public ResponseEntity<ApiResponse<List<ShiftSwapRequest>>> getPending(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(ApiResponse.success("Pending requests retrieved",
                swapService.getPendingRequests(restaurantId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ShiftSwapRequest>> createRequest(@RequestBody CreateSwapRequest request) {
        ShiftSwapRequest swap = swapService.createRequest(
                request.restaurantId, request.requestingEmployeeId,
                request.targetEmployeeId, request.scheduleId,
                request.shiftDate, request.reason);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Swap request created", swap));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<ApiResponse<ShiftSwapRequest>> accept(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Request accepted", swapService.acceptRequest(id)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<ShiftSwapRequest>> reject(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Request rejected", swapService.rejectRequest(id)));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<ShiftSwapRequest>> approve(
            @PathVariable Long id, @RequestParam Long managerId) {
        return ResponseEntity.ok(ApiResponse.success("Request approved", swapService.approveRequest(id, managerId)));
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreateSwapRequest {
        private Long restaurantId;
        private Long requestingEmployeeId;
        private Long targetEmployeeId;
        private Long scheduleId;
        private LocalDate shiftDate;
        private String reason;
    }
}
