package com.elcafe.modules.loyalty.controller;

import com.elcafe.modules.loyalty.dto.BonusTransactionResponse;
import com.elcafe.modules.loyalty.dto.CustomerLoyaltyResponse;
import com.elcafe.modules.loyalty.dto.LoyaltyConfigRequest;
import com.elcafe.modules.loyalty.dto.TierRequest;
import com.elcafe.modules.loyalty.dto.WalletTopUpResponse;
import com.elcafe.modules.loyalty.entity.BonusTransaction;
import com.elcafe.modules.loyalty.entity.CustomerLoyalty;
import com.elcafe.modules.loyalty.entity.CustomerTier;
import com.elcafe.modules.loyalty.entity.LoyaltyConfig;
import com.elcafe.modules.loyalty.entity.WalletTopUp;
import com.elcafe.modules.loyalty.mapper.LoyaltyMapper;
import com.elcafe.modules.loyalty.repository.CustomerTierRepository;
import com.elcafe.modules.loyalty.service.BonusService;
import com.elcafe.modules.loyalty.service.LoyaltyService;
import com.elcafe.modules.loyalty.service.TierService;
import com.elcafe.modules.loyalty.service.WalletTopUpService;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/loyalty")
@Tag(name = "Loyalty", description = "Customer loyalty and bonus program endpoints")
@SecurityRequirement(name = "Bearer Authentication")
@RequiredArgsConstructor
public class LoyaltyController {

    private final LoyaltyService loyaltyService;
    private final BonusService bonusService;
    private final TierService tierService;
    private final WalletTopUpService walletTopUpService;
    private final CustomerTierRepository customerTierRepository;
    private final LoyaltyMapper loyaltyMapper;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    @GetMapping("/customers/{customerId}")
    @Operation(summary = "Get customer loyalty info", description = "Get loyalty balance and tier information for a customer")
    public ResponseEntity<ApiResponse<CustomerLoyaltyResponse>> getCustomerLoyalty(
            @PathVariable Long customerId) {
        log.info("Getting loyalty info for customer {}", customerId);

        CustomerLoyalty loyalty = loyaltyService.getOrCreateCustomerLoyalty(customerId);
        CustomerLoyaltyResponse response = loyaltyMapper.toResponse(loyalty);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/customers/{customerId}/transactions")
    @Operation(summary = "Get bonus transaction history", description = "Get paginated bonus transaction history for a customer")
    public ResponseEntity<ApiResponse<Page<BonusTransactionResponse>>> getTransactionHistory(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.info("Getting transaction history for customer {}: page={}, size={}", customerId, page, size);

        CustomerLoyalty loyalty = loyaltyService.getOrCreateCustomerLoyalty(customerId);
        Pageable pageable = PageRequest.of(page, size);
        Page<BonusTransaction> transactions = bonusService.getTransactionHistory(loyalty.getId(), pageable);

        Page<BonusTransactionResponse> response = transactions.map(loyaltyMapper::toResponse);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/tiers")
    @Operation(summary = "Get all tiers", description = "Get list of all loyalty tiers, with the customer count on each tier")
    public ResponseEntity<ApiResponse<List<CustomerLoyaltyResponse.TierInfo>>> getAllTiers() {
        log.info("Getting all loyalty tiers");

        List<CustomerTier> tiers = customerTierRepository.findAll();
        List<CustomerLoyaltyResponse.TierInfo> response = tiers.stream()
                .map(tier -> loyaltyMapper.toTierInfo(tier, tierService.countCustomersOnTier(tier.getId())))
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/tiers")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @Operation(summary = "Create loyalty tier")
    public ResponseEntity<ApiResponse<CustomerTier>> createTier(@Valid @RequestBody TierRequest request) {
        log.info("Creating loyalty tier {} (level {})", request.getName(), request.getLevel());
        CustomerTier tier = tierService.createTier(request);
        return ResponseEntity.ok(ApiResponse.success("Tier created", tier));
    }

    @PutMapping("/tiers/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @Operation(summary = "Update loyalty tier")
    public ResponseEntity<ApiResponse<CustomerTier>> updateTier(
            @PathVariable Long id,
            @Valid @RequestBody TierRequest request) {
        log.info("Updating loyalty tier {}", id);
        CustomerTier tier = tierService.updateTier(id, request);
        return ResponseEntity.ok(ApiResponse.success("Tier updated", tier));
    }

    @DeleteMapping("/tiers/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @Operation(summary = "Delete loyalty tier", description = "Refuses if any customers are currently on this tier")
    public ResponseEntity<ApiResponse<Void>> deleteTier(@PathVariable Long id) {
        log.info("Deleting loyalty tier {}", id);
        tierService.deleteTier(id);
        return ResponseEntity.ok(ApiResponse.success("Tier deleted", null));
    }

    @GetMapping("/config")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER')")
    @Operation(summary = "Get loyalty config",
            description = "This restaurant's loyalty settings, or null if it has not configured any. "
                    + "Omit restaurantId to get your own restaurant's.")
    public ResponseEntity<ApiResponse<LoyaltyConfig>> getConfig(
            @RequestParam(required = false) Long restaurantId) {
        restaurantAuthorizationService.checkAccessIfPresent(restaurantId);
        // An omitted restaurantId used to mean "the global config", which no longer exists. Resolve it
        // to the caller's own restaurant instead — the thing they almost certainly meant — rather than
        // returning null and letting the settings page render empty for no visible reason.
        Long target = restaurantId != null
                ? restaurantId
                : restaurantAuthorizationService.currentTenantReadScopeStrict();
        LoyaltyConfig config = loyaltyService.getConfig(target);
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PutMapping("/config")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    @Operation(summary = "Upsert loyalty config")
    public ResponseEntity<ApiResponse<LoyaltyConfig>> upsertConfig(
            @Valid @RequestBody LoyaltyConfigRequest request) {
        restaurantAuthorizationService.checkAccess(request.getRestaurantId());
        LoyaltyConfig saved = loyaltyService.upsertConfig(request);
        return ResponseEntity.ok(ApiResponse.success("Loyalty config saved", saved));
    }

    @PostMapping("/customers/{customerId}/birthday-bonus")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Grant birthday bonus", description = "Manually grant birthday bonus to a customer")
    public ResponseEntity<ApiResponse<Void>> grantBirthdayBonus(@PathVariable Long customerId) {
        log.info("Granting birthday bonus to customer {}", customerId);

        loyaltyService.grantBirthdayBonus(customerId);

        return ResponseEntity.ok(ApiResponse.success("Birthday bonus granted successfully", null));
    }

    @PostMapping("/customers/{customerId}/reactivation-bonus")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Grant reactivation bonus", description = "Manually grant reactivation bonus to inactive customer")
    public ResponseEntity<ApiResponse<Void>> grantReactivationBonus(@PathVariable Long customerId) {
        log.info("Granting reactivation bonus to customer {}", customerId);

        loyaltyService.grantReactivationBonus(customerId);

        return ResponseEntity.ok(ApiResponse.success("Reactivation bonus granted successfully", null));
    }

    // ============== Wallet top-ups (admin view + manual confirm) ==============

    @GetMapping("/wallet/top-ups")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER')")
    @Operation(summary = "List wallet top-ups",
            description = "Paged list across all customers, optionally filtered by status")
    public ResponseEntity<ApiResponse<Page<WalletTopUpResponse>>> listTopUps(
            @RequestParam(required = false) WalletTopUp.Status status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<WalletTopUpResponse> result = walletTopUpService
                .listAll(status, PageRequest.of(page, size))
                .map(WalletTopUpResponse::from);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/wallet/top-ups/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER', 'CASHIER')")
    @Operation(summary = "Manually confirm a top-up",
            description = "Admin path for cash-at-counter (MANUAL provider) or to settle a stuck PENDING. " +
                    "Credits the wallet via the same idempotent ledger path the webhooks use.")
    public ResponseEntity<ApiResponse<WalletTopUpResponse>> manualConfirm(
            @PathVariable Long id,
            @RequestParam(required = false) String reference) {
        WalletTopUp topUp = walletTopUpService.complete(id, null, reference, null);
        return ResponseEntity.ok(ApiResponse.success("Top-up confirmed", WalletTopUpResponse.from(topUp)));
    }

    @PostMapping("/wallet/top-ups/{id}/fail")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER')")
    @Operation(summary = "Mark a top-up as failed")
    public ResponseEntity<ApiResponse<WalletTopUpResponse>> markFailed(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {
        WalletTopUp topUp = walletTopUpService.fail(id, reason);
        return ResponseEntity.ok(ApiResponse.success("Top-up failed", WalletTopUpResponse.from(topUp)));
    }
}
