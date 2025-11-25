package com.elcafe.modules.menu.controller;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.menu.dto.CreateProductRequest;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.service.MenuService;
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

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product management APIs")
public class ProductController {

    private final MenuService menuService;
    private final CategoryRepository categoryRepository;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create product", description = "Create a new product")
    public ResponseEntity<ApiResponse<Product>> createProduct(
            @Valid @RequestBody CreateProductRequest request
    ) {
        log.info("Creating product: {} for category: {}", request.getName(), request.getCategoryId());

        // Validate category exists
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", request.getCategoryId()));

        // Build product entity
        Product product = Product.builder()
                .category(category)
                .name(request.getName())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .price(request.getPrice())
                .itemType(request.getItemType())
                .sortOrder(request.getSortOrder())
                .status(request.getStatus())
                .inStock(request.getInStock())
                .featured(request.getFeatured())
                .hasVariants(request.getHasVariants())
                .build();

        Product createdProduct = menuService.createProduct(product);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", createdProduct));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get product by ID", description = "Get a single product by its ID")
    public ResponseEntity<ApiResponse<Product>> getProductById(@PathVariable Long id) {
        log.info("Fetching product: {}", id);

        Product product = menuService.getProductById(id);

        return ResponseEntity.ok(ApiResponse.success("Product retrieved successfully", product));
    }

    @GetMapping("/category/{categoryId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @Operation(summary = "Get products by category", description = "Get all products for a specific category")
    public ResponseEntity<ApiResponse<List<Product>>> getProductsByCategory(@PathVariable Long categoryId) {
        log.info("Fetching products for category: {}", categoryId);

        List<Product> products = menuService.getProductsByCategory(categoryId);

        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully", products));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete product", description = "Delete a product")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable Long id) {
        log.info("Deleting product: {}", id);

        menuService.deleteProduct(id);

        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully", null));
    }
}
