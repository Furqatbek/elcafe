package com.elcafe.modules.menu.controller;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.menu.dto.CreateCategoryRequest;
import com.elcafe.modules.menu.dto.UpdateCategoryRequest;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.service.MenuService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Category management APIs")
public class CategoryController {

    private final MenuService menuService;
    private final RestaurantRepository restaurantRepository;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create category", description = "Create a new category for a restaurant")
    public ResponseEntity<ApiResponse<Category>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        log.info("Creating category: {} for restaurant: {}", request.getName(), request.getRestaurantId());

        // Validate restaurant exists
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", request.getRestaurantId()));

        // Build category entity
        Category category = Category.builder()
                .restaurant(restaurant)
                .name(request.getName())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .sortOrder(request.getSortOrder())
                .active(request.getActive())
                .build();

        Category createdCategory = menuService.createCategory(category);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created successfully", createdCategory));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update category", description = "Update an existing category")
    public ResponseEntity<ApiResponse<Category>> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCategoryRequest request
    ) {
        log.info("Updating category: {}", id);

        // Get existing category
        Category existingCategory = menuService.getCategoryById(id);

        // Build updated category
        Category categoryData = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : existingCategory.getSortOrder())
                .active(request.getActive() != null ? request.getActive() : existingCategory.getActive())
                .build();

        Category updatedCategory = menuService.updateCategory(id, categoryData);

        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", updatedCategory));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete category", description = "Delete a category")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        log.info("Deleting category: {}", id);

        menuService.deleteCategory(id);

        return ResponseEntity.ok(ApiResponse.success("Category deleted successfully", null));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get category by ID", description = "Get a single category by its ID")
    public ResponseEntity<ApiResponse<Category>> getCategoryById(@PathVariable Long id) {
        log.info("Fetching category: {}", id);

        Category category = menuService.getCategoryById(id);

        return ResponseEntity.ok(ApiResponse.success("Category retrieved successfully", category));
    }
}
