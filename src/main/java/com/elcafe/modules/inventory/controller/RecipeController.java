package com.elcafe.modules.inventory.controller;

import com.elcafe.utils.ApiResponse;
import com.elcafe.modules.inventory.dto.RecipeRequest;
import com.elcafe.modules.inventory.dto.RecipeResponse;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.ProductIngredient;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.menu.service.ProductCostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/inventory/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private final InventoryProductIngredientRepository productIngredientRepository;
    private final ProductRepository productRepository;
    private final InventoryIngredientRepository ingredientRepository;
    private final InventoryService inventoryService;
    private final ProductCostService productCostService;

    @GetMapping("/product/{productId}")
    public ResponseEntity<ApiResponse<List<RecipeResponse>>> getProductRecipe(
            @PathVariable Long productId) {
        log.info("Fetching recipe for product: {}", productId);

        List<ProductIngredient> recipes = productIngredientRepository.findByProductIdWithIngredients(productId);
        List<RecipeResponse> responses = recipes.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Product recipe retrieved successfully", responses));
    }

    @GetMapping("/ingredient/{ingredientId}")
    public ResponseEntity<ApiResponse<List<RecipeResponse>>> getIngredientUsage(
            @PathVariable Long ingredientId) {
        log.info("Fetching usage for ingredient: {}", ingredientId);

        List<ProductIngredient> recipes = productIngredientRepository.findByIngredientIdWithProductAndIngredient(ingredientId);
        List<RecipeResponse> responses = recipes.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Ingredient usage retrieved successfully", responses));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RecipeResponse>> createRecipe(
            @Valid @RequestBody RecipeRequest request) {
        log.info("Creating recipe: product={}, ingredient={}", request.getProductId(), request.getIngredientId());

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + request.getProductId()));

        Ingredient ingredient = ingredientRepository.findById(request.getIngredientId())
                .orElseThrow(() -> new RuntimeException("Ingredient not found with id: " + request.getIngredientId()));

        ProductIngredient recipe = ProductIngredient.builder()
                .product(product)
                .ingredient(ingredient)
                .quantityRequired(request.getQuantityRequired())
                .unit(request.getUnit())
                .notes(request.getNotes())
                .optional(request.getOptional() != null ? request.getOptional() : false)
                .build();

        ProductIngredient savedRecipe = productIngredientRepository.save(recipe);

        // Recalculate product cost based on new ingredient
        productCostService.recalculateProductCost(request.getProductId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Recipe created successfully", mapToResponse(savedRecipe)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<RecipeResponse>> updateRecipe(
            @PathVariable Long id,
            @Valid @RequestBody RecipeRequest request) {
        log.info("Updating recipe: {}", id);

        // Use query that eagerly fetches product and ingredient to avoid LazyInitializationException
        ProductIngredient recipe = productIngredientRepository.findByIdWithProductAndIngredient(id)
                .orElseThrow(() -> new RuntimeException("Recipe not found with id: " + id));

        recipe.setQuantityRequired(request.getQuantityRequired());
        recipe.setUnit(request.getUnit());
        recipe.setNotes(request.getNotes());
        recipe.setOptional(request.getOptional() != null ? request.getOptional() : false);

        ProductIngredient updatedRecipe = productIngredientRepository.save(recipe);

        // Recalculate product cost based on updated ingredient quantity
        productCostService.recalculateProductCost(recipe.getProduct().getId());

        return ResponseEntity.ok(ApiResponse.success("Recipe updated successfully", mapToResponse(updatedRecipe)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteRecipe(@PathVariable Long id) {
        log.info("Deleting recipe: {}", id);

        // Use query that eagerly fetches product to avoid LazyInitializationException
        ProductIngredient recipe = productIngredientRepository.findByIdWithProductAndIngredient(id)
                .orElseThrow(() -> new RuntimeException("Recipe not found with id: " + id));

        Long productId = recipe.getProduct().getId();
        productIngredientRepository.deleteById(id);

        // Recalculate product cost after removing ingredient
        productCostService.recalculateProductCost(productId);

        return ResponseEntity.ok(ApiResponse.success("Recipe deleted successfully", null));
    }

    @GetMapping("/product/{productId}/check-availability")
    public ResponseEntity<ApiResponse<Boolean>> checkProductAvailability(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") Integer quantity) {
        log.info("Checking availability for product: {} (quantity: {})", productId, quantity);

        boolean available = inventoryService.canMakeProduct(productId, quantity);

        String message = available
                ? "Product is available"
                : "Product is not available - insufficient ingredients";

        return ResponseEntity.ok(ApiResponse.success(message, available));
    }

    @PostMapping("/product/{productId}/recalculate-cost")
    public ResponseEntity<ApiResponse<BigDecimal>> recalculateProductCost(
            @PathVariable Long productId) {
        log.info("Recalculating cost for product: {}", productId);

        BigDecimal newCost = productCostService.recalculateProductCost(productId);

        return ResponseEntity.ok(ApiResponse.success("Product cost recalculated successfully", newCost));
    }

    @PostMapping("/recalculate-all-costs")
    public ResponseEntity<ApiResponse<Integer>> recalculateAllProductCosts() {
        log.info("Recalculating cost for all products");

        int updatedCount = productCostService.recalculateAllProductCosts();

        return ResponseEntity.ok(ApiResponse.success(
                "Product costs recalculated successfully. Updated: " + updatedCount + " products",
                updatedCount));
    }

    @GetMapping("/product/{productId}/cost-breakdown")
    public ResponseEntity<ApiResponse<List<ProductCostService.IngredientCostBreakdown>>> getCostBreakdown(
            @PathVariable Long productId) {
        log.info("Getting cost breakdown for product: {}", productId);

        List<ProductCostService.IngredientCostBreakdown> breakdown = productCostService.getCostBreakdown(productId);

        return ResponseEntity.ok(ApiResponse.success("Cost breakdown retrieved successfully", breakdown));
    }

    private RecipeResponse mapToResponse(ProductIngredient recipe) {
        return RecipeResponse.builder()
                .id(recipe.getId())
                .productId(recipe.getProduct().getId())
                .productName(recipe.getProduct().getName())
                .ingredient(RecipeResponse.IngredientInfo.builder()
                        .id(recipe.getIngredient().getId())
                        .name(recipe.getIngredient().getName())
                        .unit(recipe.getIngredient().getUnit())
                        .sku(recipe.getIngredient().getSku())
                        .currentStock(recipe.getIngredient().getCurrentStock())
                        .build())
                .quantityRequired(recipe.getQuantityRequired())
                .unit(recipe.getUnit())
                .notes(recipe.getNotes())
                .optional(recipe.getOptional())
                .createdAt(recipe.getCreatedAt())
                .updatedAt(recipe.getUpdatedAt())
                .build();
    }
}
