package com.elcafe.modules.pos.cashdrawer.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.pos.cashdrawer.dto.*;
import com.elcafe.modules.pos.cashdrawer.entity.CashDrawer;
import com.elcafe.modules.pos.cashdrawer.entity.CashDrawerOperation;
import com.elcafe.modules.pos.cashdrawer.service.CashDrawerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/pos/cash-drawers")
@RequiredArgsConstructor
@Tag(name = "Cash Drawer", description = "Cash drawer management and operations")
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER', 'OPERATOR', 'CASHIER', 'WAITER')")
public class CashDrawerController {

    private final CashDrawerService cashDrawerService;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    @PostMapping
    @Operation(summary = "Create a new cash drawer")
    public ResponseEntity<CashDrawer> createCashDrawer(
            @PathVariable Long restaurantId,
            @Valid @RequestBody CreateCashDrawerRequest request) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(cashDrawerService.createCashDrawer(restaurantId, request));
    }

    @GetMapping
    @Operation(summary = "Get all cash drawers")
    public ResponseEntity<List<CashDrawer>> getCashDrawers(
            @PathVariable Long restaurantId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(cashDrawerService.getCashDrawers(restaurantId));
    }

    @PostMapping("/{drawerId}/open")
    @Operation(summary = "Open a cash drawer")
    public ResponseEntity<CashDrawerOperation> openDrawer(
            @PathVariable Long restaurantId,
            @PathVariable Long drawerId,
            @RequestParam Long operatorId,
            @RequestParam(required = false) Long shiftId,
            @RequestParam(required = false) String reason) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(cashDrawerService.openDrawer(drawerId, operatorId, shiftId, reason));
    }

    @PostMapping("/{drawerId}/paid-in")
    @Operation(summary = "Record cash paid in (float, etc.)")
    public ResponseEntity<CashDrawerOperation> paidIn(
            @PathVariable Long restaurantId,
            @PathVariable Long drawerId,
            @RequestParam BigDecimal amount,
            @RequestParam Long operatorId,
            @RequestParam(required = false) Long shiftId,
            @RequestParam(required = false) String reason) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(cashDrawerService.recordPaidIn(drawerId, amount, operatorId, shiftId, reason));
    }

    @PostMapping("/{drawerId}/paid-out")
    @Operation(summary = "Record cash paid out (expense, etc.)")
    public ResponseEntity<CashDrawerOperation> paidOut(
            @PathVariable Long restaurantId,
            @PathVariable Long drawerId,
            @RequestParam BigDecimal amount,
            @RequestParam Long operatorId,
            @RequestParam(required = false) Long shiftId,
            @RequestParam(required = false) String reason) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(cashDrawerService.recordPaidOut(drawerId, amount, operatorId, shiftId, reason));
    }

    @PostMapping("/{drawerId}/drop")
    @Operation(summary = "Record cash drop to safe/bank")
    public ResponseEntity<CashDrawerOperation> cashDrop(
            @PathVariable Long restaurantId,
            @PathVariable Long drawerId,
            @RequestParam BigDecimal amount,
            @RequestParam Long operatorId,
            @RequestParam(required = false) Long shiftId,
            @RequestParam(required = false) String notes) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(cashDrawerService.recordCashDrop(drawerId, amount, operatorId, shiftId, notes));
    }

    @PostMapping("/{drawerId}/pickup")
    @Operation(summary = "Record manager cash pickup")
    public ResponseEntity<CashDrawerOperation> cashPickup(
            @PathVariable Long restaurantId,
            @PathVariable Long drawerId,
            @RequestParam BigDecimal amount,
            @RequestParam Long operatorId,
            @RequestParam(required = false) Long shiftId,
            @RequestParam(required = false) String notes) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(cashDrawerService.recordCashPickup(drawerId, amount, operatorId, shiftId, notes));
    }

    @PostMapping("/{drawerId}/close")
    @Operation(summary = "Close drawer and count")
    public ResponseEntity<DrawerCloseResult> closeDrawer(
            @PathVariable Long restaurantId,
            @PathVariable Long drawerId,
            @RequestParam Long operatorId,
            @RequestParam(required = false) Long shiftId,
            @RequestParam BigDecimal countedAmount) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(cashDrawerService.closeDrawer(drawerId, operatorId, shiftId, countedAmount));
    }

    @GetMapping("/{drawerId}/status")
    @Operation(summary = "Get drawer status")
    public ResponseEntity<DrawerStatusResponse> getStatus(
            @PathVariable Long restaurantId,
            @PathVariable Long drawerId,
            @RequestParam(required = false) Long shiftId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(cashDrawerService.getDrawerStatus(drawerId, shiftId));
    }

    @GetMapping("/{drawerId}/history")
    @Operation(summary = "Get operation history")
    public ResponseEntity<Page<CashDrawerOperation>> getHistory(
            @PathVariable Long restaurantId,
            @PathVariable Long drawerId,
            Pageable pageable) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(cashDrawerService.getOperationHistory(drawerId, pageable));
    }

    @GetMapping("/shifts/{shiftId}/operations")
    @Operation(summary = "Get operations for a shift")
    public ResponseEntity<List<CashDrawerOperationDTO>> getShiftOperations(
            @PathVariable Long restaurantId,
            @PathVariable Long shiftId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(cashDrawerService.getShiftOperations(shiftId));
    }
}
