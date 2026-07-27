package com.elcafe.modules.customer.controller;

import com.elcafe.utils.ApiResponse;
import com.elcafe.modules.customer.dto.*;
import com.elcafe.modules.customer.service.CustomerProfileService;
import com.elcafe.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The customer 360 view (V183).
 *
 * <p>Split from {@code CustomerController} because it is a different kind of surface: that one is CRUD
 * over a record, this one assembles a read-only picture of a person from half a dozen places.
 *
 * <p>Gated to the restaurant's managers rather than all front-of-house staff. This page concentrates a
 * guest's phone, birthday, spend and entire message history into one screen — useful to whoever runs the
 * restaurant, more than a waiter taking an order needs to see. Tenant boundary is enforced underneath by
 * the service, where another restaurant's customer id reads as not-found.
 */
@RestController
@RequestMapping("/api/v1/customers/{customerId}")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER')")
@Tag(name = "Customer Profile", description = "Customer 360: preferences, purchases, loyalty, conversations")
@SecurityRequirement(name = "Bearer Authentication")
public class CustomerProfileController {

    private final CustomerProfileService profileService;

    @GetMapping("/profile")
    @Operation(summary = "Full customer profile",
            description = "Identity, preferences, purchase statistics and loyalty standing. Conversation history is paged separately.")
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> getProfile(@PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.success(profileService.getProfile(customerId)));
    }

    /**
     * A window of the guest's conversations, newest first.
     *
     * @param before feed back {@code nextCursor} from the previous response to load older messages.
     *               Omit for the most recent window.
     */
    @GetMapping("/timeline")
    @Operation(summary = "Conversation history",
            description = "Merged Instagram/Telegram/SMS history, newest first, cursored on timestamp.")
    public ResponseEntity<ApiResponse<CustomerTimelineResponse>> getTimeline(
            @PathVariable Long customerId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime before,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiResponse.success(profileService.getTimeline(customerId, before, limit)));
    }

    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<List<CustomerPreferenceResponse>>> listPreferences(
            @PathVariable Long customerId) {
        return ResponseEntity.ok(ApiResponse.success(profileService.listPreferences(customerId)));
    }

    @PostMapping("/preferences")
    @Operation(summary = "Record a preference",
            description = "Always stored as MANUAL — a caller cannot claim a preference was inferred.")
    public ResponseEntity<ApiResponse<CustomerPreferenceResponse>> addPreference(
            @PathVariable Long customerId,
            @Valid @RequestBody CustomerPreferenceRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        var created = profileService.addPreference(
                customerId, request, principal != null ? principal.getId() : null);
        return ResponseEntity.ok(ApiResponse.success(created));
    }

    @DeleteMapping("/preferences/{preferenceId}")
    public ResponseEntity<ApiResponse<Void>> deletePreference(
            @PathVariable Long customerId, @PathVariable Long preferenceId) {
        profileService.deletePreference(customerId, preferenceId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
