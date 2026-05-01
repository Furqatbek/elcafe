package com.elcafe.modules.inventory.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.security.UserPrincipal;
import com.elcafe.utils.ApiResponse;
import com.elcafe.modules.inventory.dto.AddStockRequest;
import com.elcafe.modules.inventory.dto.AdjustStockRequest;
import com.elcafe.modules.inventory.dto.IngredientRequest;
import com.elcafe.modules.inventory.dto.IngredientResponse;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryTransaction;
import com.elcafe.modules.inventory.entity.Supplier;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.SupplierRepository;
import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.inventory.service.StockOperationService;
import com.elcafe.modules.inventory.service.StockOperationService.StockReconciliationResult;
import com.elcafe.modules.menu.service.ProductCostService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/inventory/ingredients")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
public class InventoryIngredientController {

    private final InventoryIngredientRepository ingredientRepository;
    private final com.elcafe.modules.inventory.repository.IngredientCategoryRepository ingredientCategoryRepository;
    private final RestaurantRepository restaurantRepository;
    private final SupplierRepository supplierRepository;
    private final InventoryService inventoryService;
    private final ProductCostService productCostService;
    private final StockOperationService stockOperationService;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<IngredientResponse>>> getIngredients(
            @RequestParam Long restaurantId,
            @RequestParam(required = false) Long categoryId) {
        // Validate restaurant access - prevents IDOR
        restaurantAuthorizationService.validateRestaurantAccess(restaurantId);
        log.info("Fetching ingredients for restaurant: {}, category: {}", restaurantId, categoryId);

        List<Ingredient> ingredients;
        if (categoryId != null) {
            ingredients = ingredientRepository.findByRestaurantIdAndCategoryId(restaurantId, categoryId);
        } else {
            ingredients = ingredientRepository.findByRestaurant_Id(restaurantId);
        }
        List<IngredientResponse> responses = ingredients.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Ingredients retrieved successfully", responses));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<IngredientResponse>> getIngredientById(@PathVariable Long id) {
        log.info("Fetching ingredient: {}", id);

        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ingredient not found with id: " + id));

        // Validate restaurant access - prevents IDOR
        restaurantAuthorizationService.validateRestaurantAccess(ingredient.getRestaurant().getId());

        return ResponseEntity.ok(ApiResponse.success("Ingredient retrieved successfully", mapToResponse(ingredient)));
    }

    @GetMapping("/low-stock")
    public ResponseEntity<ApiResponse<List<IngredientResponse>>> getLowStockIngredients(
            @RequestParam Long restaurantId) {
        // Validate restaurant access - prevents IDOR
        restaurantAuthorizationService.validateRestaurantAccess(restaurantId);
        log.info("Fetching low stock ingredients for restaurant: {}", restaurantId);

        List<Ingredient> ingredients = ingredientRepository.findLowStockIngredients(restaurantId);
        List<IngredientResponse> responses = ingredients.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Low stock ingredients retrieved successfully", responses));
    }

    @GetMapping("/reorder")
    public ResponseEntity<ApiResponse<List<IngredientResponse>>> getReorderIngredients(
            @RequestParam Long restaurantId) {
        // Validate restaurant access - prevents IDOR
        restaurantAuthorizationService.validateRestaurantAccess(restaurantId);
        log.info("Fetching reorder ingredients for restaurant: {}", restaurantId);

        List<Ingredient> ingredients = ingredientRepository.findIngredientsNeedingReorder(restaurantId);
        List<IngredientResponse> responses = ingredients.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success("Reorder ingredients retrieved successfully", responses));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<IngredientResponse>> createIngredient(
            @Valid @RequestBody IngredientRequest request) {
        // Validate restaurant access - prevents IDOR
        restaurantAuthorizationService.validateRestaurantAccess(request.getRestaurantId());
        log.info("Creating ingredient: {}", request.getName());

        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new RuntimeException("Restaurant not found with id: " + request.getRestaurantId()));

        Supplier supplier = null;
        if (request.getSupplierId() != null) {
            supplier = supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + request.getSupplierId()));
        }

        com.elcafe.modules.inventory.entity.IngredientCategory category = null;
        if (request.getCategoryId() != null) {
            category = ingredientCategoryRepository.findById(request.getCategoryId()).orElse(null);
        }

        Ingredient ingredient = Ingredient.builder()
                .restaurant(restaurant)
                .category(category)
                .name(request.getName())
                .description(request.getDescription())
                .unit(request.getUnit())
                .currentStock(request.getCurrentStock())
                .minimumStock(request.getMinimumStock())
                .reorderLevel(request.getReorderLevel())
                .reorderQuantity(request.getReorderQuantity())
                .costPerUnit(request.getCostPerUnit())
                .supplier(request.getSupplier())
                .supplierEntity(supplier)
                .sku(request.getSku())
                .active(request.getActive() != null ? request.getActive() : true)
                .trackInventory(request.getTrackInventory() != null ? request.getTrackInventory() : true)
                .trackExpiry(request.getTrackExpiry() != null ? request.getTrackExpiry() : false)
                .defaultShelfLifeDays(request.getDefaultShelfLifeDays())
                .expiryAlertDays(request.getExpiryAlertDays() != null ? request.getExpiryAlertDays() : 7)
                .build();

        Ingredient savedIngredient = ingredientRepository.save(ingredient);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ingredient created successfully", mapToResponse(savedIngredient)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<IngredientResponse>> updateIngredient(
            @PathVariable Long id,
            @Valid @RequestBody IngredientRequest request) {
        log.info("Updating ingredient: {}", id);

        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ingredient not found with id: " + id));

        // Validate restaurant access - prevents IDOR
        restaurantAuthorizationService.validateRestaurantAccess(ingredient.getRestaurant().getId());

        Supplier supplier = null;
        if (request.getSupplierId() != null) {
            supplier = supplierRepository.findById(request.getSupplierId())
                    .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + request.getSupplierId()));
        }

        if (request.getCategoryId() != null) {
            ingredient.setCategory(ingredientCategoryRepository.findById(request.getCategoryId()).orElse(null));
        } else {
            ingredient.setCategory(null);
        }
        ingredient.setName(request.getName());
        ingredient.setDescription(request.getDescription());
        ingredient.setUnit(request.getUnit());
        ingredient.setCurrentStock(request.getCurrentStock());
        ingredient.setMinimumStock(request.getMinimumStock());
        ingredient.setReorderLevel(request.getReorderLevel());
        ingredient.setReorderQuantity(request.getReorderQuantity());
        ingredient.setCostPerUnit(request.getCostPerUnit());
        ingredient.setSupplier(request.getSupplier());
        ingredient.setSupplierEntity(supplier);
        ingredient.setSku(request.getSku());
        ingredient.setActive(request.getActive());
        ingredient.setTrackInventory(request.getTrackInventory());
        ingredient.setTrackExpiry(request.getTrackExpiry());
        ingredient.setDefaultShelfLifeDays(request.getDefaultShelfLifeDays());
        ingredient.setExpiryAlertDays(request.getExpiryAlertDays());

        Ingredient updatedIngredient = ingredientRepository.save(ingredient);

        // Recalculate cost for all products using this ingredient
        productCostService.recalculateProductsUsingIngredient(id);

        return ResponseEntity.ok(ApiResponse.success("Ingredient updated successfully", mapToResponse(updatedIngredient)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteIngredient(@PathVariable Long id) {
        log.info("Deleting ingredient: {}", id);

        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ingredient not found with id: " + id));

        // Validate restaurant access - prevents IDOR
        restaurantAuthorizationService.validateRestaurantAccess(ingredient.getRestaurant().getId());

        ingredientRepository.deleteById(id);

        return ResponseEntity.ok(ApiResponse.success("Ingredient deleted successfully", null));
    }

    @PostMapping("/{id}/add-stock")
    public ResponseEntity<ApiResponse<IngredientResponse>> addStock(
            @PathVariable Long id,
            @Valid @RequestBody AddStockRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Adding stock to ingredient: {}", id);

        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ingredient not found with id: " + id));

        // Validate restaurant access - prevents IDOR
        restaurantAuthorizationService.validateRestaurantAccess(ingredient.getRestaurant().getId());

        // Use authenticated user's email instead of user-provided performedBy to prevent falsified audit trails
        String performedBy = String.format("%s (ID:%d)", currentUser.getEmail(), currentUser.getId());

        inventoryService.addStock(
                id,
                request.getQuantity(),
                request.getNotes(),
                performedBy
        );

        // Reload ingredient after stock update
        ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ingredient not found with id: " + id));

        return ResponseEntity.ok(ApiResponse.success("Stock added successfully", mapToResponse(ingredient)));
    }

    /**
     * Adjust stock for an ingredient. This is a sensitive operation that allows
     * direct stock manipulation, so it requires MANAGER or above role.
     * All adjustments are logged with authenticated user identity.
     */
    @PostMapping("/{id}/adjust-stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'MANAGER')")
    public ResponseEntity<ApiResponse<IngredientResponse>> adjustStock(
            @PathVariable Long id,
            @Valid @RequestBody AdjustStockRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        log.info("Adjusting stock for ingredient: {} by user: {} (role: {})",
                id, currentUser.getEmail(), currentUser.getRole());

        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ingredient not found with id: " + id));

        // Validate restaurant access - prevents IDOR
        restaurantAuthorizationService.validateRestaurantAccess(ingredient.getRestaurant().getId());

        // Use authenticated user's email instead of user-provided performedBy to prevent falsified audit trails
        String performedBy = String.format("%s (ID:%d)", currentUser.getEmail(), currentUser.getId());

        inventoryService.adjustStock(
                id,
                request.getNewQuantity(),
                request.getNotes(),
                performedBy
        );

        // Reload ingredient after stock update
        ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ingredient not found with id: " + id));

        return ResponseEntity.ok(ApiResponse.success("Stock adjusted successfully", mapToResponse(ingredient)));
    }

    @GetMapping("/{id}/transactions")
    public ResponseEntity<ApiResponse<List<InventoryTransaction>>> getTransactions(
            @PathVariable Long id) {
        log.info("Fetching transactions for ingredient: {}", id);

        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ingredient not found with id: " + id));

        // Validate restaurant access - prevents IDOR
        restaurantAuthorizationService.validateRestaurantAccess(ingredient.getRestaurant().getId());

        List<InventoryTransaction> transactions = inventoryService.getTransactionHistory(id);

        return ResponseEntity.ok(ApiResponse.success("Transactions retrieved successfully", transactions));
    }

    /**
     * Reconcile stock for a single ingredient - check consistency between
     * Ingredient.currentStock and sum of InventoryBatch quantities.
     */
    @GetMapping("/{id}/reconcile")
    public ResponseEntity<ApiResponse<StockReconciliationResult>> reconcileIngredient(
            @PathVariable Long id) {
        log.info("Reconciling stock for ingredient: {}", id);

        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ingredient not found with id: " + id));

        // Validate restaurant access - prevents IDOR
        restaurantAuthorizationService.validateRestaurantAccess(ingredient.getRestaurant().getId());

        StockReconciliationResult result = stockOperationService.reconcileStock(id);

        String message = result.consistent()
                ? "Stock is consistent"
                : "Stock discrepancy detected";

        return ResponseEntity.ok(ApiResponse.success(message, result));
    }

    /**
     * Reconcile all ingredients for a restaurant - returns only discrepancies.
     * This is useful for scheduled audits and manual inventory checks.
     */
    @GetMapping("/reconcile")
    public ResponseEntity<ApiResponse<List<StockReconciliationResult>>> reconcileAllIngredients(
            @RequestParam Long restaurantId) {
        // Validate restaurant access - prevents IDOR
        restaurantAuthorizationService.validateRestaurantAccess(restaurantId);
        log.info("Reconciling all ingredients for restaurant: {}", restaurantId);

        List<StockReconciliationResult> discrepancies = stockOperationService.reconcileAllStock(restaurantId);

        String message = discrepancies.isEmpty()
                ? "All stock is consistent"
                : String.format("Found %d ingredient(s) with stock discrepancies", discrepancies.size());

        return ResponseEntity.ok(ApiResponse.success(message, discrepancies));
    }

    private IngredientResponse mapToResponse(Ingredient ingredient) {
        Supplier supplierEntity = ingredient.getSupplierEntity();
        com.elcafe.modules.inventory.entity.IngredientCategory cat = ingredient.getCategory();
        return IngredientResponse.builder()
                .id(ingredient.getId())
                .restaurantId(ingredient.getRestaurant().getId())
                .restaurantName(ingredient.getRestaurant().getName())
                .categoryId(cat != null ? cat.getId() : null)
                .categoryName(cat != null ? cat.getName() : null)
                .name(ingredient.getName())
                .description(ingredient.getDescription())
                .unit(ingredient.getUnit())
                .currentStock(ingredient.getCurrentStock())
                .minimumStock(ingredient.getMinimumStock())
                .reorderLevel(ingredient.getReorderLevel())
                .reorderQuantity(ingredient.getReorderQuantity())
                .costPerUnit(ingredient.getCostPerUnit())
                .supplier(ingredient.getSupplier())
                .supplierId(supplierEntity != null ? supplierEntity.getId() : null)
                .supplierName(supplierEntity != null ? supplierEntity.getName() : ingredient.getSupplier())
                .sku(ingredient.getSku())
                .active(ingredient.getActive())
                .trackInventory(ingredient.getTrackInventory())
                .trackExpiry(ingredient.getTrackExpiry())
                .defaultShelfLifeDays(ingredient.getDefaultShelfLifeDays())
                .expiryAlertDays(ingredient.getExpiryAlertDays())
                .createdAt(ingredient.getCreatedAt())
                .updatedAt(ingredient.getUpdatedAt())
                .build();
    }
}
