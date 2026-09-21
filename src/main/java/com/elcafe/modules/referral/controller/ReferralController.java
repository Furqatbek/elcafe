package com.elcafe.modules.referral.controller;

import com.elcafe.modules.referral.dto.*;
import com.elcafe.modules.referral.service.ReferralService;
import com.elcafe.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/referrals")
@RequiredArgsConstructor
public class ReferralController {

    private final ReferralService referralService;

    // ==================== Settings Endpoints ====================

    /**
     * Get referral program settings
     */
    @GetMapping("/settings")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ReferralSettingsResponse>> getSettings(
            @PathVariable Long restaurantId) {
        ReferralSettingsResponse settings = referralService.getSettings(restaurantId);
        return ResponseEntity.ok(ApiResponse.success(settings));
    }

    /**
     * Create or update referral program settings
     */
    @PostMapping("/settings")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ReferralSettingsResponse>> saveSettings(
            @PathVariable Long restaurantId,
            @Valid @RequestBody ReferralSettingsRequest request) {
        ReferralSettingsResponse settings = referralService.saveSettings(restaurantId, request);
        return ResponseEntity.ok(ApiResponse.success("Referral settings saved successfully", settings));
    }

    /**
     * Toggle referral program active status
     */
    @PostMapping("/settings/toggle")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ReferralSettingsResponse>> toggleProgram(
            @PathVariable Long restaurantId) {
        ReferralSettingsResponse settings = referralService.toggleProgram(restaurantId);
        String status = Boolean.TRUE.equals(settings.getProgramActive()) ? "activated" : "deactivated";
        return ResponseEntity.ok(ApiResponse.success("Referral program " + status, settings));
    }

    // ==================== Referral Code Endpoints ====================

    /**
     * Generate a referral code for a customer
     */
    @PostMapping("/codes/generate")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CUSTOMER')")
    public ResponseEntity<ApiResponse<ReferralCodeResponse>> generateCode(
            @PathVariable Long restaurantId,
            @RequestParam Long customerId) {
        ReferralCodeResponse code = referralService.generateReferralCode(restaurantId, customerId);
        return ResponseEntity.ok(ApiResponse.success("Referral code generated", code));
    }

    /**
     * Get a customer's referral code
     */
    @GetMapping("/codes/customer/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CUSTOMER')")
    public ResponseEntity<ApiResponse<ReferralCodeResponse>> getCustomerCode(
            @PathVariable Long restaurantId,
            @PathVariable Long customerId) {
        ReferralCodeResponse code = referralService.getCustomerReferralCode(restaurantId, customerId);
        return ResponseEntity.ok(ApiResponse.success(code));
    }

    /**
     * Get all referral codes (admin)
     */
    @GetMapping("/codes")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Page<ReferralCodeResponse>>> getCodes(
            @PathVariable Long restaurantId,
            Pageable pageable) {
        Page<ReferralCodeResponse> codes = referralService.getReferralCodes(restaurantId, pageable);
        return ResponseEntity.ok(ApiResponse.success(codes));
    }

    /**
     * Validate a referral code
     */
    @GetMapping("/codes/validate")
    public ResponseEntity<ApiResponse<Boolean>> validateCode(
            @PathVariable Long restaurantId,
            @RequestParam String code) {
        boolean valid = referralService.validateReferralCode(code, restaurantId);
        return ResponseEntity.ok(ApiResponse.success(valid));
    }

    // ==================== Referral Processing Endpoints ====================

    /**
     * Apply a referral code (new customer signup with referral)
     */
    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<ReferralResponse>> applyReferral(
            @PathVariable Long restaurantId,
            @Valid @RequestBody ApplyReferralRequest request) {
        ReferralResponse referral = referralService.processReferral(
                restaurantId, request.getCode(), request.getCustomerId());
        return ResponseEntity.ok(ApiResponse.success("Referral applied successfully", referral));
    }

    // ==================== Referral Tracking Endpoints ====================

    /**
     * Get all referrals (admin)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Page<ReferralResponse>>> getReferrals(
            @PathVariable Long restaurantId,
            Pageable pageable) {
        Page<ReferralResponse> referrals = referralService.getReferrals(restaurantId, pageable);
        return ResponseEntity.ok(ApiResponse.success(referrals));
    }

    /**
     * Get referrals by a specific customer
     */
    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'CUSTOMER')")
    public ResponseEntity<ApiResponse<List<ReferralResponse>>> getCustomerReferrals(
            @PathVariable Long restaurantId,
            @PathVariable Long customerId) {
        List<ReferralResponse> referrals = referralService.getCustomerReferrals(restaurantId, customerId);
        return ResponseEntity.ok(ApiResponse.success(referrals));
    }

    /**
     * Get referral statistics
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ReferralStatsResponse>> getStats(
            @PathVariable Long restaurantId) {
        ReferralStatsResponse stats = referralService.getStats(restaurantId);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
