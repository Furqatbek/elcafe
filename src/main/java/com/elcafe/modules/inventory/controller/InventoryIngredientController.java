package com.elcafe.modules.inventory.controller;

import com.elcafe.utils.ApiResponse;
import com.elcafe.modules.inventory.dto.AddStockRequest;
import com.elcafe.modules.inventory.dto.AdjustStockRequest;
import com.elcafe.modules.inventory.dto.IngredientRequest;
import com.elcafe.modules.inventory.dto.IngredientResponse;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryTransaction;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/inventory/ingredients")
@RequiredArgsConstructor
public class InventoryIngredientController {

    private final InventoryIngredientRepository ingredientRepository;
    private final RestaurantRepository restaurantRepository;
    private final InventoryService inventoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<IngredientResponse>>> getIngredients(
            @RequestParam Long restaurantId) {
        log.info("Fetching ingredients for restaurant: {}", restaurantId);

        List<Ingredient> ingredients = ingredientRepository.findByRestaurantId(restaurantId);
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

        return ResponseEntity.ok(ApiResponse.success("Ingredient retrieved successfully", mapToResponse(ingredient)));
    }

    @GetMapping("/low-stock")
    public ResponseEntity<ApiResponse<List<IngredientResponse>>> getLowStockIngredients(
            @RequestParam Long restaurantId) {
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
        log.info("Creating ingredient: {}", request.getName());

        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new RuntimeException("Restaurant not found with id: " + request.getRestaurantId()));

        Ingredient ingredient = Ingredient.builder()
                .restaurant(restaurant)
                .name(request.getName())
                .description(request.getDescription())
                .unit(request.getUnit())
                .currentStock(request.getCurrentStock())
                .minimumStock(request.getMinimumStock())
                .reorderLevel(request.getReorderLevel())
                .costPerUnit(request.getCostPerUnit())
                .supplier(request.getSupplier())
                .sku(request.getSku())
                .active(request.getActive() != null ? request.getActive() : true)
                .trackInventory(request.getTrackInventory() != null ? request.getTrackInventory() : true)
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

        ingredient.setName(request.getName());
        ingredient.setDescription(request.getDescription());
        ingredient.setUnit(request.getUnit());
        ingredient.setCurrentStock(request.getCurrentStock());
        ingredient.setMinimumStock(request.getMinimumStock());
        ingredient.setReorderLevel(request.getReorderLevel());
        ingredient.setCostPerUnit(request.getCostPerUnit());
        ingredient.setSupplier(request.getSupplier());
        ingredient.setSku(request.getSku());
        ingredient.setActive(request.getActive());
        ingredient.setTrackInventory(request.getTrackInventory());

        Ingredient updatedIngredient = ingredientRepository.save(ingredient);

        return ResponseEntity.ok(ApiResponse.success("Ingredient updated successfully", mapToResponse(updatedIngredient)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteIngredient(@PathVariable Long id) {
        log.info("Deleting ingredient: {}", id);

        if (!ingredientRepository.existsById(id)) {
            throw new RuntimeException("Ingredient not found with id: " + id);
        }

        ingredientRepository.deleteById(id);

        return ResponseEntity.ok(ApiResponse.success("Ingredient deleted successfully", null));
    }

    @PostMapping("/{id}/add-stock")
    public ResponseEntity<ApiResponse<IngredientResponse>> addStock(
            @PathVariable Long id,
            @Valid @RequestBody AddStockRequest request) {
        log.info("Adding stock to ingredient: {}", id);

        inventoryService.addStock(
                id,
                request.getQuantity(),
                request.getNotes(),
                request.getPerformedBy()
        );

        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ingredient not found with id: " + id));

        return ResponseEntity.ok(ApiResponse.success("Stock added successfully", mapToResponse(ingredient)));
    }

    @PostMapping("/{id}/adjust-stock")
    public ResponseEntity<ApiResponse<IngredientResponse>> adjustStock(
            @PathVariable Long id,
            @Valid @RequestBody AdjustStockRequest request) {
        log.info("Adjusting stock for ingredient: {}", id);

        inventoryService.adjustStock(
                id,
                request.getNewQuantity(),
                request.getNotes(),
                request.getPerformedBy()
        );

        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ingredient not found with id: " + id));

        return ResponseEntity.ok(ApiResponse.success("Stock adjusted successfully", mapToResponse(ingredient)));
    }

    @GetMapping("/{id}/transactions")
    public ResponseEntity<ApiResponse<List<InventoryTransaction>>> getTransactions(
            @PathVariable Long id) {
        log.info("Fetching transactions for ingredient: {}", id);

        Ingredient ingredient = ingredientRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ingredient not found with id: " + id));

        List<InventoryTransaction> transactions = inventoryService.getTransactionHistory(id);

        return ResponseEntity.ok(ApiResponse.success("Transactions retrieved successfully", transactions));
    }

    private IngredientResponse mapToResponse(Ingredient ingredient) {
        return IngredientResponse.builder()
                .id(ingredient.getId())
                .restaurantId(ingredient.getRestaurant().getId())
                .restaurantName(ingredient.getRestaurant().getName())
                .name(ingredient.getName())
                .description(ingredient.getDescription())
                .unit(ingredient.getUnit())
                .currentStock(ingredient.getCurrentStock())
                .minimumStock(ingredient.getMinimumStock())
                .reorderLevel(ingredient.getReorderLevel())
                .costPerUnit(ingredient.getCostPerUnit())
                .supplier(ingredient.getSupplier())
                .sku(ingredient.getSku())
                .active(ingredient.getActive())
                .trackInventory(ingredient.getTrackInventory())
                .createdAt(ingredient.getCreatedAt())
                .updatedAt(ingredient.getUpdatedAt())
                .build();
    }
}
