package com.elcafe.modules.menu.controller;

import com.elcafe.modules.menu.dto.*;
import com.elcafe.modules.menu.service.MenuCollectionService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/menu-collections")
@RequiredArgsConstructor
@Tag(name = "Menu Collections", description = "Menu collection management APIs")
public class MenuCollectionController {

    private final MenuCollectionService menuCollectionService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get menu collections")
    public ResponseEntity<ApiResponse<Page<MenuCollectionDTO>>> getMenuCollections(
            @RequestParam Long restaurantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<MenuCollectionDTO> collections = menuCollectionService.getMenuCollections(restaurantId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Menu collections retrieved successfully", collections));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get menu collection by ID")
    public ResponseEntity<ApiResponse<MenuCollectionDTO>> getMenuCollectionById(@PathVariable Long id) {
        MenuCollectionDTO collection = menuCollectionService.getMenuCollectionById(id);
        return ResponseEntity.ok(ApiResponse.success("Menu collection retrieved successfully", collection));
    }

    @GetMapping("/active")
    @Operation(summary = "Get active menu collections (public)")
    public ResponseEntity<ApiResponse<List<MenuCollectionDTO>>> getActiveMenuCollections(
            @RequestParam Long restaurantId
    ) {
        List<MenuCollectionDTO> collections = menuCollectionService.getActiveMenuCollections(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Active menu collections retrieved", collections));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create menu collection")
    public ResponseEntity<ApiResponse<MenuCollectionDTO>> createMenuCollection(
            @Valid @RequestBody CreateMenuCollectionRequest request
    ) {
        MenuCollectionDTO collection = menuCollectionService.createMenuCollection(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Menu collection created successfully", collection));
    }

    @PostMapping("/{id}/products")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Add products to menu collection")
    public ResponseEntity<ApiResponse<Void>> addProducts(
            @PathVariable Long id,
            @RequestBody List<Long> productIds
    ) {
        menuCollectionService.addProductsToCollection(id, productIds);
        return ResponseEntity.ok(ApiResponse.success("Products added to collection", null));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete menu collection")
    public ResponseEntity<ApiResponse<Void>> deleteMenuCollection(@PathVariable Long id) {
        menuCollectionService.deleteMenuCollection(id);
        return ResponseEntity.ok(ApiResponse.success("Menu collection deleted successfully", null));
    }
}
