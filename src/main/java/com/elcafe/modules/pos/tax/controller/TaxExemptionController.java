package com.elcafe.modules.pos.tax.controller;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.pos.tax.dto.*;
import com.elcafe.modules.pos.tax.entity.TaxExemptionLog;
import com.elcafe.modules.pos.tax.entity.TaxExemptionType;
import com.elcafe.modules.pos.tax.service.TaxExemptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/pos/tax-exemptions")
@RequiredArgsConstructor
@Tag(name = "Tax Exemptions", description = "Tax exemption management")
public class TaxExemptionController {

    private final TaxExemptionService taxExemptionService;

    // Exemption Types
    @PostMapping("/types")
    @Operation(summary = "Create a tax exemption type")
    public ResponseEntity<TaxExemptionType> createType(
            @PathVariable Long restaurantId,
            @Valid @RequestBody CreateTaxExemptionTypeRequest request) {
        return ResponseEntity.ok(taxExemptionService.createExemptionType(restaurantId, request));
    }

    @GetMapping("/types")
    @Operation(summary = "Get all exemption types")
    public ResponseEntity<List<TaxExemptionType>> getTypes(
            @PathVariable Long restaurantId) {
        return ResponseEntity.ok(taxExemptionService.getExemptionTypes(restaurantId));
    }

    // Customer Exemptions
    @PostMapping("/customers/{customerId}")
    @Operation(summary = "Set customer as tax exempt")
    public ResponseEntity<Customer> setCustomerTaxExempt(
            @PathVariable Long restaurantId,
            @PathVariable Long customerId,
            @Valid @RequestBody SetCustomerTaxExemptRequest request) {
        return ResponseEntity.ok(taxExemptionService.setCustomerTaxExempt(customerId, request));
    }

    @DeleteMapping("/customers/{customerId}")
    @Operation(summary = "Remove tax exempt status from customer")
    public ResponseEntity<Customer> removeCustomerTaxExempt(
            @PathVariable Long restaurantId,
            @PathVariable Long customerId) {
        return ResponseEntity.ok(taxExemptionService.removeCustomerTaxExempt(customerId));
    }

    @GetMapping("/customers/{customerId}/check")
    @Operation(summary = "Check customer tax exemption status")
    public ResponseEntity<TaxExemptCheckResult> checkCustomerExemption(
            @PathVariable Long restaurantId,
            @PathVariable Long customerId) {
        return ResponseEntity.ok(taxExemptionService.checkCustomerExemption(customerId));
    }

    // Order Exemptions
    @PostMapping("/orders/{orderId}")
    @Operation(summary = "Apply tax exemption to an order")
    public ResponseEntity<TaxExemptionResult> applyToOrder(
            @PathVariable Long restaurantId,
            @PathVariable Long orderId,
            @Valid @RequestBody ApplyTaxExemptionRequest request,
            @RequestParam Long operatorId) {
        return ResponseEntity.ok(taxExemptionService.applyOrderTaxExemption(orderId, request, operatorId));
    }

    @DeleteMapping("/orders/{orderId}")
    @Operation(summary = "Remove tax exemption from an order")
    public ResponseEntity<?> removeFromOrder(
            @PathVariable Long restaurantId,
            @PathVariable Long orderId,
            @RequestParam BigDecimal taxRate) {
        return ResponseEntity.ok(taxExemptionService.removeOrderTaxExemption(orderId, taxRate));
    }

    // Logs
    @GetMapping("/logs")
    @Operation(summary = "Get exemption logs")
    public ResponseEntity<Page<TaxExemptionLog>> getLogs(
            @PathVariable Long restaurantId,
            Pageable pageable) {
        return ResponseEntity.ok(taxExemptionService.getExemptionLogs(restaurantId, pageable));
    }
}
