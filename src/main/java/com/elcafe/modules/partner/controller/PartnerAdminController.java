package com.elcafe.modules.partner.controller;

import com.elcafe.modules.partner.dto.CreatePartnerRequest;
import com.elcafe.modules.partner.dto.PartnerAdminResponse;
import com.elcafe.modules.partner.dto.PartnerGrantRequest;
import com.elcafe.modules.partner.dto.PartnerKeyResponse;
import com.elcafe.modules.partner.service.PartnerAdminService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Staff-side management of integration partners.
 *
 * <p>SUPER_ADMIN throughout, and deliberately so. A partner is the one entity here that spans tenants:
 * its key opens whichever venues it is granted, so if a restaurant admin could manage partners they
 * could grant a partner access to a venue that is not theirs. Keeping the whole surface at platform
 * level closes that by construction rather than by a check someone has to remember. It also matches how
 * these deals are actually struck — an aggregator signs with the platform, not with one restaurant.
 *
 * <p>Path is {@code /api/v1/partners} (plural) — distinct from the partner-facing
 * {@code /api/v1/partner/**}, which is the API-key surface and is closed to staff tokens.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/partners")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Partner administration", description = "Manage delivery-aggregator integrations")
public class PartnerAdminController {

    private final PartnerAdminService partnerAdminService;

    @GetMapping
    @Operation(summary = "List partners", description = "All integration partners and their venue grants")
    public ResponseEntity<ApiResponse<List<PartnerAdminResponse>>> listPartners() {
        return ResponseEntity.ok(ApiResponse.success(partnerAdminService.listPartners()));
    }

    @PostMapping
    @Operation(summary = "Create a partner",
            description = "Returns the raw API key. This is the ONLY time it is available — only its "
                    + "hash is stored, so it cannot be retrieved later, only rotated.")
    public ResponseEntity<ApiResponse<PartnerKeyResponse>> createPartner(
            @Valid @RequestBody CreatePartnerRequest request) {
        PartnerKeyResponse created = partnerAdminService.createPartner(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Partner created. Copy the API key now — it cannot be shown again.", created));
    }

    @PostMapping("/{partnerId}/rotate-key")
    @Operation(summary = "Rotate a partner's API key",
            description = "Issues a new key and kills the old one immediately, with no overlap.")
    public ResponseEntity<ApiResponse<PartnerKeyResponse>> rotateKey(@PathVariable Long partnerId) {
        PartnerKeyResponse rotated = partnerAdminService.rotateKey(partnerId);
        return ResponseEntity.ok(ApiResponse.success(
                "Key rotated. The previous key no longer works — copy this one now.", rotated));
    }

    @PatchMapping("/{partnerId}/active")
    @Operation(summary = "Activate or deactivate a partner",
            description = "Deactivating is the kill switch: every venue goes dark at once, grants intact.")
    public ResponseEntity<ApiResponse<PartnerAdminResponse>> setActive(
            @PathVariable Long partnerId,
            @RequestParam boolean active) {
        return ResponseEntity.ok(ApiResponse.success(partnerAdminService.setActive(partnerId, active)));
    }

    @PutMapping("/{partnerId}/restaurants/{restaurantId}")
    @Operation(summary = "Grant a venue to a partner",
            description = "Idempotent — re-granting updates the capabilities in place.")
    public ResponseEntity<ApiResponse<PartnerAdminResponse>> grantRestaurant(
            @PathVariable Long partnerId,
            @PathVariable Long restaurantId,
            @Valid @RequestBody PartnerGrantRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                partnerAdminService.grantRestaurant(partnerId, restaurantId, request)));
    }

    @DeleteMapping("/{partnerId}/restaurants/{restaurantId}")
    @Operation(summary = "Revoke a venue from a partner",
            description = "Deactivates the grant, keeping the record of who was once connected.")
    public ResponseEntity<ApiResponse<PartnerAdminResponse>> revokeRestaurant(
            @PathVariable Long partnerId,
            @PathVariable Long restaurantId) {
        return ResponseEntity.ok(ApiResponse.success(
                partnerAdminService.revokeRestaurant(partnerId, restaurantId)));
    }
}
