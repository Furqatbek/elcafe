package com.elcafe.modules.menu.controller;

import com.elcafe.modules.menu.dto.ProductListDTO;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.service.MenuService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Consumer-facing menu browsing endpoints
 * Allows authenticated customers to view menu, products, and categories
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/consumer/menu")
@RequiredArgsConstructor
@Tag(name = "Consumer Menu", description = "Consumer menu browsing endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class ConsumerMenuController {

    private final MenuService menuService;

    @GetMapping("/restaurant/{restaurantId}")
    @Operation(summary = "Get menu for restaurant", description = "Get full menu with categories and products for a restaurant")
    public ResponseEntity<ApiResponse<List<Category>>> getMenuForRestaurant(@PathVariable Long restaurantId) {
        log.info("Consumer fetching menu for restaurant: {}", restaurantId);
        List<Category> menu = menuService.getPublicMenu(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Menu retrieved successfully", menu));
    }

    @GetMapping("/restaurant/{restaurantId}/products")
    @Operation(summary = "Get products for restaurant", description = "Get all products for a specific restaurant")
    public ResponseEntity<ApiResponse<List<ProductListDTO>>> getProductsByRestaurant(@PathVariable Long restaurantId) {
        log.info("Consumer fetching products for restaurant: {}", restaurantId);
        List<ProductListDTO> products = menuService.getProductsByRestaurant(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully", products));
    }

    @GetMapping("/restaurant/{restaurantId}/categories")
    @Operation(summary = "Get categories for restaurant", description = "Get all categories for a specific restaurant")
    public ResponseEntity<ApiResponse<List<Category>>> getCategoriesByRestaurant(@PathVariable Long restaurantId) {
        log.info("Consumer fetching categories for restaurant: {}", restaurantId);
        List<Category> categories = menuService.getCategoriesByRestaurant(restaurantId);
        return ResponseEntity.ok(ApiResponse.success("Categories retrieved successfully", categories));
    }

    @GetMapping("/category/{categoryId}/products")
    @Operation(summary = "Get products by category", description = "Get all products for a specific category")
    public ResponseEntity<ApiResponse<List<Product>>> getProductsByCategory(@PathVariable Long categoryId) {
        log.info("Consumer fetching products for category: {}", categoryId);
        List<Product> products = menuService.getProductsByCategory(categoryId);
        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully", products));
    }

    @GetMapping("/product/{productId}")
    @Operation(summary = "Get product details", description = "Get detailed information about a specific product")
    public ResponseEntity<ApiResponse<Product>> getProductById(@PathVariable Long productId) {
        log.info("Consumer fetching product: {}", productId);
        Product product = menuService.getProductById(productId);
        return ResponseEntity.ok(ApiResponse.success("Product retrieved successfully", product));
    }
}
