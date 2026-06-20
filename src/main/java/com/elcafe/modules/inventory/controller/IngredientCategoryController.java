package com.elcafe.modules.inventory.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.inventory.entity.IngredientCategory;
import com.elcafe.modules.inventory.repository.IngredientCategoryRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.utils.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/inventory/ingredient-categories")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class IngredientCategoryController {

    private final RestaurantAuthorizationService restaurantAuthorizationService;
    private final IngredientCategoryRepository categoryRepository;
    private final RestaurantRepository restaurantRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<IngredientCategory>>> getCategories(
            @RequestParam Long restaurantId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        List<IngredientCategory> categories = categoryRepository
                .findByRestaurantIdOrderBySortOrderAscNameAsc(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Categories retrieved", categories));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<IngredientCategory>> createCategory(
            @RequestBody CreateCategoryRequest request) {
        restaurantAuthorizationService.checkAccess(request.restaurantId);
        Restaurant restaurant = restaurantRepository.findById(request.restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        if (categoryRepository.existsByRestaurantIdAndName(request.restaurantId, request.name)) {
            throw new IllegalArgumentException("Category '" + request.name + "' already exists");
        }

        IngredientCategory category = IngredientCategory.builder()
                .restaurant(restaurant)
                .name(request.name)
                .sortOrder(request.sortOrder != null ? request.sortOrder : 0)
                .build();

        category = categoryRepository.save(category);
        log.info("Created ingredient category: {} for restaurant {}", request.name, request.restaurantId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created", category));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<IngredientCategory>> updateCategory(
            @PathVariable Long id,
            @RequestBody CreateCategoryRequest request) {
        IngredientCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
        category.setName(request.name);
        if (request.sortOrder != null) category.setSortOrder(request.sortOrder);
        category = categoryRepository.save(category);
        return ResponseEntity.ok(ApiResponse.success("Category updated", category));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        categoryRepository.deleteById(id);
        log.info("Deleted ingredient category {}", id);
        return ResponseEntity.ok(ApiResponse.success("Category deleted", null));
    }

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreateCategoryRequest {
        private Long restaurantId;
        private String name;
        private Integer sortOrder;
    }
}
