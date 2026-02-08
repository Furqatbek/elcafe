package com.elcafe.modules.selfservice.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.selfservice.dto.CreateQRCodeRequest;
import com.elcafe.modules.selfservice.entity.QRCode;
import com.elcafe.modules.selfservice.entity.SelfServiceSettings;
import com.elcafe.modules.selfservice.service.QRCodeService;
import com.elcafe.modules.selfservice.service.SelfServiceOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin controller for QR code management.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/qr-codes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
@Tag(name = "QR Codes (Admin)", description = "Admin API for QR code management")
public class QRCodeController {

    private final QRCodeService qrCodeService;
    private final SelfServiceOrderService orderService;
    private final RestaurantAuthorizationService restaurantAuthService;

    // ==================== Response Helper Methods ====================

    private static Map<String, Object> successResponse(Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", data);
        return response;
    }

    private static ResponseEntity<Map<String, Object>> errorResponse(String message, int statusCode) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return ResponseEntity.status(statusCode).body(response);
    }

    /**
     * Create a new QR code.
     */
    @PostMapping
    @Operation(summary = "Create QR code", description = "Create a new QR code")
    public ResponseEntity<Map<String, Object>> createQRCode(@Valid @RequestBody CreateQRCodeRequest request) {
        try {
            restaurantAuthService.validateRestaurantAccess(request.getRestaurantId());
            QRCode qrCode = qrCodeService.createQRCode(request);
            return ResponseEntity.ok(successResponse(qrCode));
        } catch (RuntimeException e) {
            return errorResponse(e.getMessage(), 400);
        }
    }

    /**
     * Generate QR codes for all tables.
     */
    @PostMapping("/restaurant/{restaurantId}/generate-all")
    @Operation(summary = "Generate for all tables", description = "Generate QR codes for all tables in a restaurant")
    public ResponseEntity<Map<String, Object>> generateForAllTables(
            @PathVariable Long restaurantId) {

        // Validate restaurant access to prevent cross-restaurant IDOR
        restaurantAuthService.validateRestaurantAccess(restaurantId);

        try {
            List<QRCode> generated = qrCodeService.generateForAllTables(restaurantId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("generated", generated.size());
            response.put("qrCodes", generated);

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", e.getMessage());
            errorResponse.put("generated", 0);
            errorResponse.put("qrCodes", List.of());
            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * Get all QR codes for a restaurant.
     */
    @GetMapping("/restaurant/{restaurantId}")
    @Operation(summary = "Get QR codes", description = "Get all QR codes for a restaurant")
    public ResponseEntity<Page<QRCode>> getByRestaurant(
            @PathVariable Long restaurantId,
            Pageable pageable) {

        // Validate restaurant access to prevent cross-restaurant IDOR
        restaurantAuthService.validateRestaurantAccess(restaurantId);

        Page<QRCode> qrCodes = qrCodeService.getByRestaurant(restaurantId, pageable);
        return ResponseEntity.ok(qrCodes);
    }

    /**
     * Get QR code by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get QR code", description = "Get a QR code by ID")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable Long id) {
        return qrCodeService.getById(id)
                .map(qrCode -> ResponseEntity.ok(successResponse(qrCode)))
                .orElseGet(() -> errorResponse("QR code not found", 404));
    }

    /**
     * Toggle QR code active status.
     */
    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Toggle active status", description = "Toggle QR code active/inactive status")
    public ResponseEntity<Map<String, Object>> toggleActive(@PathVariable Long id) {
        try {
            QRCode qrCode = qrCodeService.toggleActive(id);
            return ResponseEntity.ok(successResponse(qrCode));
        } catch (RuntimeException e) {
            return errorResponse(e.getMessage(), 404);
        }
    }

    /**
     * Delete QR code.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete QR code", description = "Delete a QR code")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        qrCodeService.delete(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "QR code deleted"));
    }

    /**
     * Get QR code image as Base64.
     */
    @GetMapping("/{id}/image")
    @Operation(summary = "Get QR code image", description = "Get QR code image as Base64 PNG")
    public ResponseEntity<Map<String, Object>> getQRCodeImage(
            @PathVariable Long id,
            @RequestParam(defaultValue = "300") int width,
            @RequestParam(defaultValue = "300") int height) {

        return qrCodeService.getById(id)
                .map(qrCode -> {
                    try {
                        String imageBase64 = qrCodeService.generateQRCodeImage(qrCode.getCode(), width, height);
                        String currentUrl = qrCodeService.getCurrentUrl(qrCode);

                        Map<String, Object> response = new HashMap<>();
                        response.put("success", true);
                        response.put("code", qrCode.getCode());
                        response.put("url", currentUrl);
                        response.put("image", "data:image/png;base64," + imageBase64);

                        return ResponseEntity.ok(response);
                    } catch (RuntimeException e) {
                        return errorResponse("Failed to generate QR code image", 500);
                    }
                })
                .orElseGet(() -> errorResponse("QR code not found", 404));
    }

    /**
     * Get QR code statistics.
     */
    @GetMapping("/restaurant/{restaurantId}/stats")
    @Operation(summary = "Get statistics", description = "Get QR code statistics for a restaurant")
    public ResponseEntity<QRCodeService.QRCodeStats> getStats(@PathVariable Long restaurantId) {
        // Validate restaurant access to prevent cross-restaurant IDOR
        restaurantAuthService.validateRestaurantAccess(restaurantId);

        QRCodeService.QRCodeStats stats = qrCodeService.getStats(restaurantId);
        return ResponseEntity.ok(stats);
    }

    // ==================== Self-Service Settings ====================

    /**
     * Get self-service settings.
     */
    @GetMapping("/restaurant/{restaurantId}/settings")
    @Operation(summary = "Get settings", description = "Get self-service settings for a restaurant")
    public ResponseEntity<SelfServiceSettings> getSettings(@PathVariable Long restaurantId) {
        // Validate restaurant access to prevent cross-restaurant IDOR
        restaurantAuthService.validateRestaurantAccess(restaurantId);

        SelfServiceSettings settings = orderService.getSettings(restaurantId);
        if (settings == null) {
            // Return default settings if none exist
            settings = new SelfServiceSettings();
            settings.setEnabled(false);
            settings.setRequirePayment(false);
            settings.setAllowTakeaway(true);
            settings.setAllowDineIn(true);
            settings.setAutoAcceptOrders(false);
            settings.setEstimatedPrepTimeMinutes(15);
            settings.setShowWaitTime(true);
            settings.setAllowSpecialInstructions(true);
            settings.setMaxItemsPerOrder(20);
        }
        return ResponseEntity.ok(settings);
    }

    /**
     * Save self-service settings.
     */
    @PostMapping("/restaurant/{restaurantId}/settings")
    @Operation(summary = "Save settings", description = "Save self-service settings for a restaurant")
    public ResponseEntity<SelfServiceSettings> saveSettings(
            @PathVariable Long restaurantId,
            @Valid @RequestBody SelfServiceSettings settings) {

        // Validate restaurant access to prevent cross-restaurant IDOR
        restaurantAuthService.validateRestaurantAccess(restaurantId);

        SelfServiceSettings saved = orderService.saveSettings(restaurantId, settings);
        return ResponseEntity.ok(saved);
    }
}
