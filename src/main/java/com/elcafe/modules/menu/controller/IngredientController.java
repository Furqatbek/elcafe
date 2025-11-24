package com.elcafe.modules.menu.controller;

import com.elcafe.modules.menu.dto.*;
import com.elcafe.modules.menu.service.IngredientService;
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
@RequestMapping("/api/v1/ingredients")
@RequiredArgsConstructor
@Tag(name = "Ingredients", description = "Ingredient management APIs")
public class IngredientController {

    private final IngredientService ingredientService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get all ingredients", description = "Get paginated list of ingredients")
    public ResponseEntity<ApiResponse<Page<IngredientDTO>>> getAllIngredients(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sortBy
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy));
        Page<IngredientDTO> ingredients = ingredientService.getAllIngredients(pageable);
        return ResponseEntity.ok(ApiResponse.success("Ingredients retrieved successfully", ingredients));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get ingredient by ID")
    public ResponseEntity<ApiResponse<IngredientDTO>> getIngredientById(@PathVariable Long id) {
        IngredientDTO ingredient = ingredientService.getIngredientById(id);
        return ResponseEntity.ok(ApiResponse.success("Ingredient retrieved successfully", ingredient));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Search ingredients")
    public ResponseEntity<ApiResponse<Page<IngredientDTO>>> searchIngredients(
            @RequestParam String query,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<IngredientDTO> ingredients = ingredientService.searchIngredients(query, isActive, pageable);
        return ResponseEntity.ok(ApiResponse.success("Search results retrieved successfully", ingredients));
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get low stock ingredients")
    public ResponseEntity<ApiResponse<List<IngredientDTO>>> getLowStockIngredients() {
        List<IngredientDTO> ingredients = ingredientService.getLowStockIngredients();
        return ResponseEntity.ok(ApiResponse.success("Low stock ingredients retrieved", ingredients));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create ingredient")
    public ResponseEntity<ApiResponse<IngredientDTO>> createIngredient(
            @Valid @RequestBody CreateIngredientRequest request
    ) {
        IngredientDTO ingredient = ingredientService.createIngredient(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ingredient created successfully", ingredient));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update ingredient")
    public ResponseEntity<ApiResponse<IngredientDTO>> updateIngredient(
            @PathVariable Long id,
            @Valid @RequestBody UpdateIngredientRequest request
    ) {
        IngredientDTO ingredient = ingredientService.updateIngredient(id, request);
        return ResponseEntity.ok(ApiResponse.success("Ingredient updated successfully", ingredient));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete ingredient")
    public ResponseEntity<ApiResponse<Void>> deleteIngredient(@PathVariable Long id) {
        ingredientService.deleteIngredient(id);
        return ResponseEntity.ok(ApiResponse.success("Ingredient deleted successfully", null));
    }
}
