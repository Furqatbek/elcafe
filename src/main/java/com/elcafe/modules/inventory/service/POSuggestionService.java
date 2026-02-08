package com.elcafe.modules.inventory.service;

import com.elcafe.modules.financial.entity.PurchaseOrder;
import com.elcafe.modules.financial.entity.PurchaseOrderItem;
import com.elcafe.modules.financial.repository.PurchaseOrderItemRepository;
import com.elcafe.modules.financial.repository.PurchaseOrderRepository;
import com.elcafe.modules.financial.service.PurchaseOrderService;
import com.elcafe.modules.inventory.dto.GeneratePORequest;
import com.elcafe.modules.inventory.dto.POSuggestionItemResponse;
import com.elcafe.modules.inventory.dto.POSuggestionResponse;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.Supplier;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.SupplierRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class POSuggestionService {

    private final InventoryIngredientRepository ingredientRepository;
    private final SupplierRepository supplierRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final PurchaseOrderService purchaseOrderService;
    private final RestaurantRepository restaurantRepository;

    /**
     * Get all PO suggestions for a restaurant, grouped by supplier
     */
    @Transactional(readOnly = true)
    public List<POSuggestionResponse> getSuggestions(Long restaurantId) {
        log.info("Generating PO suggestions for restaurant: {}", restaurantId);

        // Get all ingredients that need reordering (with linked suppliers)
        // Use JOIN FETCH method to avoid N+1 queries when accessing supplier
        List<Ingredient> ingredientsNeedingReorder = ingredientRepository.findByRestaurantIdWithSupplier(restaurantId)
                .stream()
                .filter(ing -> ing.getActive() && ing.getTrackInventory())
                .filter(Ingredient::needsReorder)
                .filter(ing -> ing.getSupplierEntity() != null) // Must have a linked supplier
                .collect(Collectors.toList());

        if (ingredientsNeedingReorder.isEmpty()) {
            log.info("No ingredients need reordering for restaurant: {}", restaurantId);
            return Collections.emptyList();
        }

        // Get ingredient IDs that already have pending purchase orders
        Set<Long> ingredientIdsWithPendingPOs = getIngredientIdsWithPendingPOs(restaurantId);

        // Filter out ingredients with pending POs
        ingredientsNeedingReorder = ingredientsNeedingReorder.stream()
                .filter(ing -> !ingredientIdsWithPendingPOs.contains(ing.getId()))
                .collect(Collectors.toList());

        if (ingredientsNeedingReorder.isEmpty()) {
            log.info("All ingredients needing reorder already have pending POs");
            return Collections.emptyList();
        }

        // Group by supplier
        Map<Long, List<Ingredient>> bySupplier = ingredientsNeedingReorder.stream()
                .collect(Collectors.groupingBy(ing -> ing.getSupplierEntity().getId()));

        // Build suggestions for each supplier
        List<POSuggestionResponse> suggestions = new ArrayList<>();
        for (Map.Entry<Long, List<Ingredient>> entry : bySupplier.entrySet()) {
            Supplier supplier = entry.getValue().get(0).getSupplierEntity();
            List<Ingredient> ingredients = entry.getValue();

            POSuggestionResponse suggestion = buildSuggestion(supplier, ingredients);
            suggestions.add(suggestion);
        }

        // Sort by urgency (CRITICAL first, then HIGH, then MEDIUM)
        suggestions.sort(Comparator.comparing(POSuggestionResponse::getUrgency));

        log.info("Generated {} PO suggestions for restaurant: {}", suggestions.size(), restaurantId);
        return suggestions;
    }

    /**
     * Generate a draft purchase order from a suggestion
     */
    @Transactional
    public PurchaseOrder generatePurchaseOrder(GeneratePORequest request, String createdBy) {
        log.info("Generating PO for supplier: {} in restaurant: {}", request.getSupplierId(), request.getRestaurantId());

        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        Supplier supplier = supplierRepository.findById(request.getSupplierId())
                .orElseThrow(() -> new RuntimeException("Supplier not found"));

        // Get ingredients to include
        List<Ingredient> ingredients;
        if (request.getIngredientIds() != null && !request.getIngredientIds().isEmpty()) {
            // Specific ingredients requested
            ingredients = ingredientRepository.findAllById(request.getIngredientIds());
        } else {
            // All ingredients from this supplier that need reorder
            // Use JOIN FETCH method to avoid N+1 queries when accessing supplier
            ingredients = ingredientRepository.findByRestaurantIdWithSupplier(request.getRestaurantId())
                    .stream()
                    .filter(ing -> ing.getActive() && ing.getTrackInventory())
                    .filter(Ingredient::needsReorder)
                    .filter(ing -> ing.getSupplierEntity() != null && ing.getSupplierEntity().getId().equals(request.getSupplierId()))
                    .collect(Collectors.toList());
        }

        if (ingredients.isEmpty()) {
            throw new RuntimeException("No ingredients to order");
        }

        // Build PO
        String supplierContact = supplier.getContactPerson() != null ?
                supplier.getContactPerson() + (supplier.getPhone() != null ? " - " + supplier.getPhone() : "") :
                supplier.getPhone();

        PurchaseOrder po = PurchaseOrder.builder()
                .restaurant(restaurant)
                .supplier(supplier)
                .supplierName(supplier.getName())
                .supplierContact(supplierContact)
                .supplierAddress(supplier.getAddress())
                .orderDate(LocalDate.now())
                .expectedDeliveryDate(request.getExpectedDeliveryDate())
                .notes(request.getNotes() != null ? request.getNotes() : "Auto-generated from reorder suggestions")
                .createdBy(createdBy)
                .build();

        // Build items
        List<PurchaseOrderItem> items = ingredients.stream()
                .map(ing -> {
                    BigDecimal suggestedQty = calculateSuggestedQuantity(ing);
                    BigDecimal unitPrice = ing.getCostPerUnit() != null ? ing.getCostPerUnit() : BigDecimal.ZERO;
                    BigDecimal totalPrice = suggestedQty.multiply(unitPrice);

                    return PurchaseOrderItem.builder()
                            .ingredient(ing)
                            .itemName(ing.getName())
                            .description(ing.getDescription())
                            .sku(ing.getSku())
                            .quantity(suggestedQty)
                            .unit(ing.getUnit() != null ? ing.getUnit() : "")
                            .unitPrice(unitPrice)
                            .totalPrice(totalPrice)
                            .receivedQuantity(BigDecimal.ZERO)
                            .build();
                })
                .collect(Collectors.toList());

        return purchaseOrderService.createPurchaseOrder(po, items);
    }

    /**
     * Get count of pending suggestions (for badge)
     */
    @Transactional(readOnly = true)
    public int getSuggestionCount(Long restaurantId) {
        List<POSuggestionResponse> suggestions = getSuggestions(restaurantId);
        return suggestions.stream()
                .mapToInt(POSuggestionResponse::getItemCount)
                .sum();
    }

    private POSuggestionResponse buildSuggestion(Supplier supplier, List<Ingredient> ingredients) {
        List<POSuggestionItemResponse> items = ingredients.stream()
                .map(this::buildSuggestionItem)
                .collect(Collectors.toList());

        BigDecimal estimatedTotal = items.stream()
                .map(POSuggestionItemResponse::getEstimatedCost)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        POSuggestionResponse.Urgency urgency = calculateUrgency(ingredients);

        return POSuggestionResponse.builder()
                .supplierId(supplier.getId())
                .supplierName(supplier.getName())
                .supplierCode(supplier.getCode())
                .supplierContact(supplier.getContactPerson() != null ?
                        supplier.getContactPerson() + (supplier.getPhone() != null ? " - " + supplier.getPhone() : "") :
                        supplier.getPhone())
                .supplierAddress(supplier.getAddress())
                .supplierPaymentTerms(supplier.getPaymentTerms())
                .items(items)
                .estimatedTotal(estimatedTotal)
                .urgency(urgency)
                .itemCount(items.size())
                .build();
    }

    private POSuggestionItemResponse buildSuggestionItem(Ingredient ingredient) {
        BigDecimal suggestedQty = calculateSuggestedQuantity(ingredient);
        BigDecimal estimatedCost = ingredient.getCostPerUnit() != null ?
                suggestedQty.multiply(ingredient.getCostPerUnit()) : null;

        return POSuggestionItemResponse.builder()
                .ingredientId(ingredient.getId())
                .ingredientName(ingredient.getName())
                .sku(ingredient.getSku())
                .unit(ingredient.getUnit())
                .currentStock(ingredient.getCurrentStock())
                .minimumStock(ingredient.getMinimumStock())
                .reorderLevel(ingredient.getReorderLevel())
                .suggestedQuantity(suggestedQty)
                .costPerUnit(ingredient.getCostPerUnit())
                .estimatedCost(estimatedCost)
                .build();
    }

    private BigDecimal calculateSuggestedQuantity(Ingredient ingredient) {
        // If reorderQuantity is set, use it
        if (ingredient.getReorderQuantity() != null && ingredient.getReorderQuantity().compareTo(BigDecimal.ZERO) > 0) {
            return ingredient.getReorderQuantity();
        }

        // Otherwise, calculate: (reorderLevel * 2) - currentStock
        // This brings stock up to twice the reorder level
        BigDecimal targetStock = ingredient.getReorderLevel().multiply(BigDecimal.valueOf(2));
        BigDecimal needed = targetStock.subtract(ingredient.getCurrentStock());

        // Ensure we don't suggest negative quantity
        return needed.compareTo(BigDecimal.ZERO) > 0 ? needed : ingredient.getReorderLevel();
    }

    private POSuggestionResponse.Urgency calculateUrgency(List<Ingredient> ingredients) {
        boolean hasCritical = ingredients.stream()
                .anyMatch(ing -> ing.getCurrentStock().compareTo(BigDecimal.ZERO) <= 0);
        if (hasCritical) {
            return POSuggestionResponse.Urgency.CRITICAL;
        }

        boolean hasHigh = ingredients.stream()
                .anyMatch(Ingredient::isLowStock);
        if (hasHigh) {
            return POSuggestionResponse.Urgency.HIGH;
        }

        return POSuggestionResponse.Urgency.MEDIUM;
    }

    private Set<Long> getIngredientIdsWithPendingPOs(Long restaurantId) {
        // Get POs that are not yet received
        List<PurchaseOrder.Status> pendingStatuses = Arrays.asList(
                PurchaseOrder.Status.DRAFT,
                PurchaseOrder.Status.PENDING_APPROVAL,
                PurchaseOrder.Status.APPROVED,
                PurchaseOrder.Status.ORDERED,
                PurchaseOrder.Status.PARTIALLY_RECEIVED
        );

        List<PurchaseOrder> pendingPOs = purchaseOrderRepository.findByRestaurant_IdAndStatusIn(
                restaurantId, pendingStatuses);

        Set<Long> ingredientIds = new HashSet<>();
        for (PurchaseOrder po : pendingPOs) {
            for (PurchaseOrderItem item : po.getItems()) {
                if (item.getIngredient() != null) {
                    ingredientIds.add(item.getIngredient().getId());
                }
            }
        }

        return ingredientIds;
    }
}
