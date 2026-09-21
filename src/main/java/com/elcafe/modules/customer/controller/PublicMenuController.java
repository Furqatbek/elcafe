package com.elcafe.modules.customer.controller;

import com.elcafe.modules.menu.dto.PublicMenuCategoryDTO;
import com.elcafe.modules.menu.dto.PublicMenuProductDTO;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.service.MenuService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Public menu endpoints for website customers (no authentication required)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/customer/public")
@RequiredArgsConstructor
@Tag(name = "Customer Public Menu", description = "Public menu browsing for website customers (no auth required)")
public class PublicMenuController {

    private final MenuService menuService;

    /**
     * Get all active categories for a restaurant
     */
    @GetMapping("/restaurants/{restaurantId}/categories")
    @Operation(summary = "Browse categories", description = "Get all active categories for a restaurant (public, no auth)")
    public ResponseEntity<ApiResponse<List<PublicMenuCategoryDTO>>> getCategories(
            @PathVariable Long restaurantId) {
        log.info("Public: Fetching categories for restaurant: {}", restaurantId);

        List<Category> categories = menuService.getActiveCategoriesByRestaurant(restaurantId);

        List<PublicMenuCategoryDTO> categoryDTOs = categories.stream()
                .map(category -> PublicMenuCategoryDTO.builder()
                        .id(category.getId())
                        .name(category.getName())
                        .description(category.getDescription())
                        .imageUrl(category.getImageUrl())
                        .sortOrder(category.getSortOrder())
                        .active(category.getActive())
                        .createdAt(category.getCreatedAt())
                        .updatedAt(category.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Categories retrieved successfully", categoryDTOs));
    }

    /**
     * Get menu items (products) by category ID
     */
    @GetMapping("/categories/{categoryId}/products")
    @Operation(summary = "Browse menu by category", description = "Get all products for a category (public, no auth)")
    public ResponseEntity<ApiResponse<List<PublicMenuProductDTO>>> getProductsByCategory(
            @PathVariable Long categoryId) {
        log.info("Public: Fetching products for category: {}", categoryId);

        List<Product> products = menuService.getProductsByCategory(categoryId);

        List<PublicMenuProductDTO> productDTOs = products.stream()
                .filter(product -> product.getInStock() != null && product.getInStock())
                .map(product -> PublicMenuProductDTO.builder()
                        .id(product.getId())
                        .name(product.getName())
                        .description(product.getDescription())
                        .imageUrl(product.getImageUrl())
                        .price(product.getPrice())
                        .priceWithMargin(product.getPriceWithMargin())
                        .itemType(product.getItemType())
                        .sortOrder(product.getSortOrder())
                        .status(product.getStatus())
                        .inStock(product.getInStock())
                        .featured(product.getFeatured())
                        .hasVariants(product.getHasVariants())
                        .createdAt(product.getCreatedAt())
                        .updatedAt(product.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully", productDTOs));
    }

    /**
     * Get full menu for a restaurant (categories with products)
     */
    @GetMapping("/restaurants/{restaurantId}/menu")
    @Operation(summary = "Get full menu", description = "Get complete menu with categories and products (public, no auth)")
    public ResponseEntity<ApiResponse<List<PublicMenuCategoryDTO>>> getFullMenu(
            @PathVariable Long restaurantId) {
        log.info("Public: Fetching full menu for restaurant: {}", restaurantId);

        List<PublicMenuCategoryDTO> menu = menuService.getPublicMenu(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Menu retrieved successfully", menu));
    }
}
