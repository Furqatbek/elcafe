package com.elcafe.modules.referral.controller;

import com.elcafe.modules.referral.dto.*;
import com.elcafe.modules.referral.service.ReferralService;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.security.CustomerPrincipal;
import com.elcafe.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/referrals")
@RequiredArgsConstructor
public class ReferralController {

    private final ReferralService referralService;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    /**
     * A CUSTOMER token may only act on its OWN customerId. Staff (ADMIN/MANAGER = a UserPrincipal, not
     * a CustomerPrincipal) may pass any customerId. Without this a logged-in consumer could read any
     * customer's referral code (name + email PII) by enumerating customerId.
     */
    private void enforceCustomerSelf(Long customerId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomerPrincipal cp
                && !Objects.equals(cp.getId(), customerId)) {
            throw new AccessDeniedException("A customer may only access their own referral code");
        }
    }

    // ==================== Settings Endpoints ====================

    /**
     * Get referral program settings
     */
    @GetMapping("/settings")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<ReferralSettingsResponse>> getSettings(
            @PathVariable Long restaurantId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
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
        restaurantAuthorizationService.checkAccess(restaurantId);
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
        restaurantAuthorizationService.checkAccess(restaurantId);
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
        restaurantAuthorizationService.checkAccess(restaurantId);
        enforceCustomerSelf(customerId);
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
        restaurantAuthorizationService.checkAccess(restaurantId);
        enforceCustomerSelf(customerId);
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
        restaurantAuthorizationService.checkAccess(restaurantId);
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
        restaurantAuthorizationService.checkAccess(restaurantId);
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
        restaurantAuthorizationService.checkAccess(restaurantId);
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
        restaurantAuthorizationService.checkAccess(restaurantId);
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
        restaurantAuthorizationService.checkAccess(restaurantId);
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
        restaurantAuthorizationService.checkAccess(restaurantId);
        ReferralStatsResponse stats = referralService.getStats(restaurantId);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
