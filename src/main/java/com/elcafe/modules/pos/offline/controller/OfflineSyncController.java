package com.elcafe.modules.pos.offline.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.pos.offline.dto.*;
import com.elcafe.modules.pos.offline.entity.OfflineOrder;
import com.elcafe.modules.pos.offline.entity.POSDevice;
import com.elcafe.modules.pos.offline.enums.OfflineSyncStatus;
import com.elcafe.modules.pos.offline.service.OfflineSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/pos/offline")
@RequiredArgsConstructor
@Tag(name = "POS Offline Mode", description = "Offline order synchronization")
@PreAuthorize("hasAnyRole('ADMIN','OWNER','MANAGER','OPERATOR','CASHIER','WAITER','SUPERVISOR','HEAD_WAITER')")
public class OfflineSyncController {

    private final OfflineSyncService offlineSyncService;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    @PostMapping("/devices/register")
    @Operation(summary = "Register a POS device for offline mode")
    public ResponseEntity<POSDevice> registerDevice(
            @PathVariable Long restaurantId,
            @Valid @RequestBody DeviceRegistrationRequest request) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(offlineSyncService.registerDevice(restaurantId, request));
    }

    @PostMapping("/devices/{deviceId}/heartbeat")
    @Operation(summary = "Record device heartbeat")
    public ResponseEntity<Void> heartbeat(
            @PathVariable Long restaurantId,
            @PathVariable String deviceId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        offlineSyncService.recordHeartbeat(restaurantId, deviceId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/devices/status")
    @Operation(summary = "Get device status (online/offline)")
    public ResponseEntity<DeviceStatusResponse> getDeviceStatus(
            @PathVariable Long restaurantId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(offlineSyncService.getDeviceStatus(restaurantId));
    }

    @PostMapping("/orders")
    @Operation(summary = "Queue an offline order for sync")
    public ResponseEntity<OfflineOrder> queueOfflineOrder(
            @PathVariable Long restaurantId,
            @Valid @RequestBody OfflineOrderRequest request) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(offlineSyncService.queueOfflineOrder(restaurantId, request));
    }

    @PostMapping("/orders/batch")
    @Operation(summary = "Queue multiple offline orders for sync")
    public ResponseEntity<List<OfflineOrder>> queueOfflineOrders(
            @PathVariable Long restaurantId,
            @Valid @RequestBody List<OfflineOrderRequest> requests) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(offlineSyncService.queueOfflineOrders(restaurantId, requests));
    }

    @PostMapping("/devices/{deviceId}/sync")
    @Operation(summary = "Sync all pending orders for a device")
    public ResponseEntity<BatchSyncResult> syncDeviceOrders(
            @PathVariable Long restaurantId,
            @PathVariable String deviceId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(offlineSyncService.syncDeviceOrders(restaurantId, deviceId));
    }

    @GetMapping("/status")
    @Operation(summary = "Get overall sync status for restaurant")
    public ResponseEntity<OfflineSyncStatus> getSyncStatus(
            @PathVariable Long restaurantId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(offlineSyncService.getSyncStatus(restaurantId));
    }
}
