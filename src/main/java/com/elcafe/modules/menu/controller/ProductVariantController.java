package com.elcafe.modules.menu.controller;

import com.elcafe.modules.menu.dto.*;
import com.elcafe.modules.menu.service.ProductVariantService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/products/{productId}/variants")
@RequiredArgsConstructor
@Tag(name = "Product Variants", description = "Product variant management APIs")
public class ProductVariantController {

    private final ProductVariantService productVariantService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get all variants for a product", description = "Get paginated list of product variants")
    public ResponseEntity<ApiResponse<Page<ProductVariantResponse>>> getAllVariants(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "sortOrder") String sortBy
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy));
        Page<ProductVariantResponse> variants = productVariantService.getAllVariantsByProduct(productId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Product variants retrieved successfully", variants));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get all variants without pagination")
    public ResponseEntity<ApiResponse<List<ProductVariantResponse>>> getAllVariantsNoPaging(
            @PathVariable Long productId
    ) {
        List<ProductVariantResponse> variants = productVariantService.getAllVariantsByProduct(productId);
        return ResponseEntity.ok(ApiResponse.success("Product variants retrieved successfully", variants));
    }

    @GetMapping("/{variantId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get variant by ID")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> getVariantById(
            @PathVariable Long productId,
            @PathVariable Long variantId
    ) {
        ProductVariantResponse variant = productVariantService.getVariantById(productId, variantId);
        return ResponseEntity.ok(ApiResponse.success("Product variant retrieved successfully", variant));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Search product variants")
    public ResponseEntity<ApiResponse<Page<ProductVariantResponse>>> searchVariants(
            @PathVariable Long productId,
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ProductVariantResponse> variants = productVariantService.searchVariants(productId, query, pageable);
        return ResponseEntity.ok(ApiResponse.success("Search results retrieved successfully", variants));
    }

    @GetMapping("/in-stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get in-stock variants")
    public ResponseEntity<ApiResponse<List<ProductVariantResponse>>> getInStockVariants(
            @PathVariable Long productId
    ) {
        List<ProductVariantResponse> variants = productVariantService.getInStockVariants(productId);
        return ResponseEntity.ok(ApiResponse.success("In-stock variants retrieved successfully", variants));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create product variant")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> createVariant(
            @PathVariable Long productId,
            @Valid @RequestBody CreateProductVariantRequest request
    ) {
        ProductVariantResponse variant = productVariantService.createVariant(productId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product variant created successfully", variant));
    }

    @PutMapping("/{variantId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update product variant")
    public ResponseEntity<ApiResponse<ProductVariantResponse>> updateVariant(
            @PathVariable Long productId,
            @PathVariable Long variantId,
            @Valid @RequestBody UpdateProductVariantRequest request
    ) {
        ProductVariantResponse variant = productVariantService.updateVariant(productId, variantId, request);
        return ResponseEntity.ok(ApiResponse.success("Product variant updated successfully", variant));
    }

    @DeleteMapping("/{variantId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete product variant")
    public ResponseEntity<ApiResponse<Void>> deleteVariant(
            @PathVariable Long productId,
            @PathVariable Long variantId
    ) {
        productVariantService.deleteVariant(productId, variantId);
        return ResponseEntity.ok(ApiResponse.success("Product variant deleted successfully", null));
    }
}
