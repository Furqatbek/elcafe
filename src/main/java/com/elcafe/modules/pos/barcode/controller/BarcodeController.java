package com.elcafe.modules.pos.barcode.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.pos.barcode.dto.BarcodeLookupResult;
import com.elcafe.modules.pos.barcode.service.BarcodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/restaurants/{restaurantId}/pos/barcode")
@RequiredArgsConstructor
@Tag(name = "Barcode Scanning", description = "Product lookup by barcode/SKU")
public class BarcodeController {

    private final BarcodeService barcodeService;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    @GetMapping("/lookup/{code}")
    @Operation(summary = "Look up product by barcode or SKU")
    public ResponseEntity<BarcodeLookupResult> lookup(
            @PathVariable Long restaurantId,
            @PathVariable String code) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(barcodeService.lookup(restaurantId, code));
    }

    @GetMapping("/exists/{code}")
    @Operation(summary = "Check if barcode/SKU exists")
    public ResponseEntity<Boolean> exists(
            @PathVariable Long restaurantId,
            @PathVariable String code) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(barcodeService.exists(restaurantId, code));
    }
}
