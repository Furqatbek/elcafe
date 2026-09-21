package com.elcafe.modules.loyalty.controller;

import com.elcafe.modules.loyalty.dto.BonusTransactionResponse;
import com.elcafe.modules.loyalty.dto.CustomerLoyaltyResponse;
import com.elcafe.modules.loyalty.entity.BonusTransaction;
import com.elcafe.modules.loyalty.entity.CustomerLoyalty;
import com.elcafe.modules.loyalty.entity.CustomerTier;
import com.elcafe.modules.loyalty.mapper.LoyaltyMapper;
import com.elcafe.modules.loyalty.repository.CustomerTierRepository;
import com.elcafe.modules.loyalty.service.BonusService;
import com.elcafe.modules.loyalty.service.LoyaltyService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    private final CustomerTierRepository customerTierRepository;
    private final LoyaltyMapper loyaltyMapper;

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
    @Operation(summary = "Get all tiers", description = "Get list of all loyalty tiers")
    public ResponseEntity<ApiResponse<List<CustomerLoyaltyResponse.TierInfo>>> getAllTiers() {
        log.info("Getting all loyalty tiers");

        List<CustomerTier> tiers = customerTierRepository.findAll();
        List<CustomerLoyaltyResponse.TierInfo> response = tiers.stream()
                .map(loyaltyMapper::toTierInfo)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(response));
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
}
