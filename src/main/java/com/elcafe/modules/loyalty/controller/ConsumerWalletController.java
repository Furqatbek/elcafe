package com.elcafe.modules.loyalty.controller;

import com.elcafe.modules.loyalty.dto.CreateTopUpRequest;
import com.elcafe.modules.loyalty.dto.CustomerLoyaltyResponse;
import com.elcafe.modules.loyalty.dto.WalletTopUpResponse;
import com.elcafe.modules.loyalty.entity.CustomerLoyalty;
import com.elcafe.modules.loyalty.entity.WalletTopUp;
import com.elcafe.modules.loyalty.mapper.LoyaltyMapper;
import com.elcafe.modules.loyalty.service.LoyaltyService;
import com.elcafe.modules.loyalty.service.WalletTopUpService;
import com.elcafe.security.CustomerPrincipal;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Consumer-facing wallet endpoints. Mounted under /api/v1/consumer
 * so they share the existing consumer OTP-auth layer — all calls
 * require an authenticated CustomerPrincipal.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/consumer/wallet")
@RequiredArgsConstructor
@Tag(name = "Consumer Wallet", description = "Customer-facing loyalty wallet and top-up flow")
@SecurityRequirement(name = "Bearer Authentication")
public class ConsumerWalletController {

    private final WalletTopUpService walletTopUpService;
    private final LoyaltyService loyaltyService;
    private final LoyaltyMapper loyaltyMapper;

    @GetMapping
    @Operation(summary = "Get own wallet",
            description = "Read the authenticated customer's loyalty wallet — balance, lifetime totals, and tier")
    public ResponseEntity<ApiResponse<CustomerLoyaltyResponse>> getOwnWallet(
            @AuthenticationPrincipal CustomerPrincipal principal) {
        CustomerLoyalty loyalty = loyaltyService.getOrCreateCustomerLoyalty(principal.getId());
        return ResponseEntity.ok(ApiResponse.success(loyaltyMapper.toResponse(loyalty)));
    }

    @PostMapping("/top-ups")
    @Operation(summary = "Create top-up",
            description = "Start a wallet top-up. Returns the top-up record including a checkout URL " +
                    "(null for MANUAL provider). Top-up stays PENDING until the provider webhook confirms.")
    public ResponseEntity<ApiResponse<WalletTopUpResponse>> create(
            @AuthenticationPrincipal CustomerPrincipal principal,
            @Valid @RequestBody CreateTopUpRequest request) {
        log.info("Customer {} creating top-up: provider={}, amount={}",
                principal.getId(), request.getProvider(), request.getAmount());
        WalletTopUp topUp = walletTopUpService.create(principal.getId(), request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Top-up created", WalletTopUpResponse.from(topUp)));
    }

    @GetMapping("/top-ups/{id}")
    @Operation(summary = "Get top-up status")
    public ResponseEntity<ApiResponse<WalletTopUpResponse>> get(
            @AuthenticationPrincipal CustomerPrincipal principal,
            @PathVariable Long id) {
        WalletTopUp topUp = walletTopUpService.getOwnedBy(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(WalletTopUpResponse.from(topUp)));
    }

    @GetMapping("/top-ups")
    @Operation(summary = "List own top-ups")
    public ResponseEntity<ApiResponse<Page<WalletTopUpResponse>>> list(
            @AuthenticationPrincipal CustomerPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<WalletTopUpResponse> result = walletTopUpService
                .listForCustomer(principal.getId(), PageRequest.of(page, size))
                .map(WalletTopUpResponse::from);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/top-ups/{id}/cancel")
    @Operation(summary = "Cancel a pending top-up",
            description = "Cancels a PENDING top-up the customer no longer intends to pay")
    public ResponseEntity<ApiResponse<WalletTopUpResponse>> cancel(
            @AuthenticationPrincipal CustomerPrincipal principal,
            @PathVariable Long id) {
        WalletTopUp topUp = walletTopUpService.cancelByCustomer(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Top-up cancelled", WalletTopUpResponse.from(topUp)));
    }
}
