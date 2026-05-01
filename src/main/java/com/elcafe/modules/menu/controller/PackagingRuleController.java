package com.elcafe.modules.menu.controller;

import com.elcafe.modules.menu.dto.CreatePackagingRuleRequest;
import com.elcafe.modules.menu.dto.PackagingRuleResponse;
import com.elcafe.modules.menu.entity.PackagingRule;
import com.elcafe.modules.menu.service.PackagingService;
import com.elcafe.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/packaging-rules")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class PackagingRuleController {

    private final PackagingService packagingService;

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<ApiResponse<List<PackagingRuleResponse>>> getRulesByRestaurant(
            @PathVariable Long restaurantId) {
        List<PackagingRuleResponse> rules = packagingService.getRulesForRestaurant(restaurantId).stream()
                .map(PackagingRuleResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Packaging rules retrieved", rules));
    }

    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<List<PackagingRuleResponse>>> getRulesByProduct(
            @PathVariable Long productId) {
        List<PackagingRuleResponse> rules = packagingService.getRulesForProduct(productId).stream()
                .map(PackagingRuleResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Packaging rules retrieved", rules));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PackagingRuleResponse>> createRule(
            @Valid @RequestBody CreatePackagingRuleRequest request) {
        log.info("Creating packaging rule: product {} → ingredient {}",
                request.getProductId(), request.getPackagingIngredientId());
        PackagingRule rule = packagingService.createRule(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Packaging rule created", PackagingRuleResponse.fromEntity(rule)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PackagingRuleResponse>> updateRule(
            @PathVariable Long id,
            @Valid @RequestBody CreatePackagingRuleRequest request) {
        log.info("Updating packaging rule {}", id);
        PackagingRule rule = packagingService.updateRule(id, request);
        return ResponseEntity.ok(ApiResponse.success("Packaging rule updated", PackagingRuleResponse.fromEntity(rule)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRule(@PathVariable Long id) {
        log.info("Deleting packaging rule {}", id);
        packagingService.deleteRule(id);
        return ResponseEntity.ok(ApiResponse.success("Packaging rule deleted", null));
    }

    @PostMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse<PackagingRuleResponse>> toggleRule(@PathVariable Long id) {
        log.info("Toggling packaging rule {}", id);
        PackagingRule rule = packagingService.toggleRule(id);
        return ResponseEntity.ok(ApiResponse.success(
                rule.getActive() ? "Packaging rule enabled" : "Packaging rule disabled",
                PackagingRuleResponse.fromEntity(rule)));
    }
}
