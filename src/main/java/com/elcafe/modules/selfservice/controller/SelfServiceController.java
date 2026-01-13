package com.elcafe.modules.selfservice.controller;

import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.selfservice.dto.AddToCartRequest;
import com.elcafe.modules.selfservice.dto.CartItemResponse;
import com.elcafe.modules.selfservice.dto.SubmitOrderRequest;
import com.elcafe.modules.selfservice.entity.QRCode;
import com.elcafe.modules.selfservice.entity.SelfServiceOrder;
import com.elcafe.modules.selfservice.entity.SelfServiceSession;
import com.elcafe.modules.selfservice.entity.SelfServiceSettings;
import com.elcafe.modules.selfservice.service.QRCodeService;
import com.elcafe.modules.selfservice.service.SelfServiceOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Public API controller for self-service ordering.
 * No authentication required - uses session tokens.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/self-service")
@RequiredArgsConstructor
@Tag(name = "Self Service", description = "Public API for QR code based self-service ordering")
public class SelfServiceController {

    private final QRCodeService qrCodeService;
    private final SelfServiceOrderService orderService;
    private final RestaurantRepository restaurantRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    // ==================== Session Management ====================

    /**
     * Start a session by scanning QR code.
     */
    @PostMapping("/session/start")
    @Operation(summary = "Start session", description = "Start a self-service session by scanning a QR code")
    public ResponseEntity<Map<String, Object>> startSession(
            @RequestParam String code,
            HttpServletRequest request) {

        String deviceInfo = request.getHeader("User-Agent");
        String ipAddress = getClientIp(request);

        SelfServiceSession session = orderService.startSession(code, deviceInfo, ipAddress);

        Map<String, Object> response = new HashMap<>();
        response.put("sessionToken", session.getSessionToken());
        response.put("restaurantId", session.getRestaurant().getId());
        response.put("restaurantName", session.getRestaurant().getName());
        response.put("tableId", session.getTable() != null ? session.getTable().getId() : null);
        response.put("tableNumber", session.getTable() != null ? session.getTable().getTableNumber() : null);
        response.put("expiresAt", session.getExpiresAt());

        return ResponseEntity.ok(response);
    }

    /**
     * Get session info.
     */
    @GetMapping("/session")
    @Operation(summary = "Get session", description = "Get current session information")
    public ResponseEntity<Map<String, Object>> getSession(
            @RequestHeader("X-Session-Token") String sessionToken) {

        SelfServiceSession session = orderService.getSession(sessionToken)
                .orElseThrow(() -> new RuntimeException("Session not found"));

        Map<String, Object> response = new HashMap<>();
        response.put("sessionToken", session.getSessionToken());
        response.put("restaurantId", session.getRestaurant().getId());
        response.put("tableId", session.getTable() != null ? session.getTable().getId() : null);
        response.put("tableNumber", session.getTable() != null ? session.getTable().getTableNumber() : null);
        response.put("customerName", session.getCustomerName());
        response.put("expiresAt", session.getExpiresAt());
        response.put("isActive", session.getIsActive());

        return ResponseEntity.ok(response);
    }

    // ==================== Menu ====================

    /**
     * Get restaurant info and settings.
     */
    @GetMapping("/restaurant/{restaurantId}")
    @Operation(summary = "Get restaurant info", description = "Get restaurant information for self-service menu")
    public ResponseEntity<Map<String, Object>> getRestaurantInfo(
            @PathVariable Long restaurantId) {

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new RuntimeException("Restaurant not found"));

        SelfServiceSettings settings = orderService.getSettings(restaurantId);

        Map<String, Object> response = new HashMap<>();
        response.put("id", restaurant.getId());
        response.put("name", restaurant.getName());
        response.put("address", restaurant.getAddress());
        response.put("phone", restaurant.getPhone());
        response.put("logoUrl", restaurant.getLogoUrl());

        if (settings != null) {
            response.put("selfServiceEnabled", settings.getEnabled());
            response.put("allowTakeaway", settings.getAllowTakeaway());
            response.put("allowDineIn", settings.getAllowDineIn());
            response.put("minimumOrderAmount", settings.getMinimumOrderAmount());
            response.put("estimatedPrepTime", settings.getEstimatedPrepTimeMinutes());
            response.put("showWaitTime", settings.getShowWaitTime());
            response.put("allowSpecialInstructions", settings.getAllowSpecialInstructions());
        } else {
            response.put("selfServiceEnabled", false);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get menu categories.
     */
    @GetMapping("/menu/{restaurantId}/categories")
    @Operation(summary = "Get categories", description = "Get menu categories for a restaurant")
    public ResponseEntity<List<Map<String, Object>>> getCategories(
            @PathVariable Long restaurantId) {

        List<Category> categories = categoryRepository.findByRestaurantIdAndActiveTrue(restaurantId);

        List<Map<String, Object>> response = categories.stream().map(cat -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", cat.getId());
            map.put("name", cat.getName());
            map.put("nameUz", cat.getNameUz());
            map.put("nameRu", cat.getNameRu());
            map.put("imageUrl", cat.getImageUrl());
            map.put("displayOrder", cat.getDisplayOrder());
            return map;
        }).toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Get products by category.
     */
    @GetMapping("/menu/{restaurantId}/products")
    @Operation(summary = "Get products", description = "Get products for a restaurant, optionally filtered by category")
    public ResponseEntity<List<Map<String, Object>>> getProducts(
            @PathVariable Long restaurantId,
            @RequestParam(required = false) Long categoryId) {

        List<Product> products;
        if (categoryId != null) {
            products = productRepository.findByCategoryIdAndAvailableTrue(categoryId);
        } else {
            products = productRepository.findByRestaurantIdAndAvailableTrue(restaurantId);
        }

        List<Map<String, Object>> response = products.stream().map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId());
            map.put("name", p.getName());
            map.put("nameUz", p.getNameUz());
            map.put("nameRu", p.getNameRu());
            map.put("description", p.getDescription());
            map.put("descriptionUz", p.getDescriptionUz());
            map.put("descriptionRu", p.getDescriptionRu());
            map.put("price", p.getPrice());
            map.put("imageUrl", p.getImageUrl());
            map.put("categoryId", p.getCategory().getId());
            map.put("preparationTime", p.getPreparationTime());
            return map;
        }).toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Get product details with variants.
     */
    @GetMapping("/menu/product/{productId}")
    @Operation(summary = "Get product details", description = "Get product details including variants and modifiers")
    public ResponseEntity<Map<String, Object>> getProductDetails(
            @PathVariable Long productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        Map<String, Object> response = new HashMap<>();
        response.put("id", product.getId());
        response.put("name", product.getName());
        response.put("nameUz", product.getNameUz());
        response.put("nameRu", product.getNameRu());
        response.put("description", product.getDescription());
        response.put("descriptionUz", product.getDescriptionUz());
        response.put("descriptionRu", product.getDescriptionRu());
        response.put("price", product.getPrice());
        response.put("imageUrl", product.getImageUrl());
        response.put("preparationTime", product.getPreparationTime());

        // Variants
        if (product.getVariants() != null && !product.getVariants().isEmpty()) {
            response.put("variants", product.getVariants().stream().map(v -> {
                Map<String, Object> vmap = new HashMap<>();
                vmap.put("id", v.getId());
                vmap.put("name", v.getName());
                vmap.put("nameUz", v.getNameUz());
                vmap.put("nameRu", v.getNameRu());
                vmap.put("price", v.getPrice());
                vmap.put("available", v.getAvailable());
                return vmap;
            }).toList());
        }

        // Linked items (modifiers)
        if (product.getLinkedItems() != null && !product.getLinkedItems().isEmpty()) {
            response.put("modifiers", product.getLinkedItems().stream()
                    .filter(li -> li.getLinkedProduct().getAvailable())
                    .map(li -> {
                        Map<String, Object> lmap = new HashMap<>();
                        lmap.put("id", li.getId());
                        lmap.put("name", li.getLinkedProduct().getName());
                        lmap.put("nameUz", li.getLinkedProduct().getNameUz());
                        lmap.put("nameRu", li.getLinkedProduct().getNameRu());
                        lmap.put("price", li.getPriceAdjustment());
                        lmap.put("linkType", li.getLinkType().name());
                        return lmap;
                    }).toList());
        }

        return ResponseEntity.ok(response);
    }

    // ==================== Cart ====================

    /**
     * Add item to cart.
     */
    @PostMapping("/cart/add")
    @Operation(summary = "Add to cart", description = "Add an item to the cart")
    public ResponseEntity<CartItemResponse> addToCart(
            @RequestHeader("X-Session-Token") String sessionToken,
            @Valid @RequestBody AddToCartRequest request) {

        var item = orderService.addToCart(sessionToken, request);
        List<CartItemResponse> cart = orderService.getCart(sessionToken);
        return ResponseEntity.ok(cart.stream()
                .filter(c -> c.getId().equals(item.getId()))
                .findFirst()
                .orElse(null));
    }

    /**
     * Update cart item quantity.
     */
    @PutMapping("/cart/item/{itemId}")
    @Operation(summary = "Update cart item", description = "Update quantity of a cart item")
    public ResponseEntity<Map<String, Object>> updateCartItem(
            @RequestHeader("X-Session-Token") String sessionToken,
            @PathVariable Long itemId,
            @RequestParam Integer quantity) {

        orderService.updateCartItem(sessionToken, itemId, quantity);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", quantity > 0 ? "Item updated" : "Item removed");

        return ResponseEntity.ok(response);
    }

    /**
     * Remove item from cart.
     */
    @DeleteMapping("/cart/item/{itemId}")
    @Operation(summary = "Remove from cart", description = "Remove an item from the cart")
    public ResponseEntity<Map<String, Object>> removeFromCart(
            @RequestHeader("X-Session-Token") String sessionToken,
            @PathVariable Long itemId) {

        orderService.removeFromCart(sessionToken, itemId);

        return ResponseEntity.ok(Map.of("success", true, "message", "Item removed"));
    }

    /**
     * Get cart contents.
     */
    @GetMapping("/cart")
    @Operation(summary = "Get cart", description = "Get current cart contents")
    public ResponseEntity<Map<String, Object>> getCart(
            @RequestHeader("X-Session-Token") String sessionToken) {

        List<CartItemResponse> items = orderService.getCart(sessionToken);

        var total = items.stream()
                .map(CartItemResponse::getTotalPrice)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        Map<String, Object> response = new HashMap<>();
        response.put("items", items);
        response.put("itemCount", items.stream().mapToInt(CartItemResponse::getQuantity).sum());
        response.put("total", total);

        return ResponseEntity.ok(response);
    }

    /**
     * Clear cart.
     */
    @DeleteMapping("/cart")
    @Operation(summary = "Clear cart", description = "Remove all items from cart")
    public ResponseEntity<Map<String, Object>> clearCart(
            @RequestHeader("X-Session-Token") String sessionToken) {

        orderService.clearCart(sessionToken);
        return ResponseEntity.ok(Map.of("success", true, "message", "Cart cleared"));
    }

    // ==================== Orders ====================

    /**
     * Submit order.
     */
    @PostMapping("/order/submit")
    @Operation(summary = "Submit order", description = "Submit the cart as an order")
    public ResponseEntity<Map<String, Object>> submitOrder(
            @RequestHeader("X-Session-Token") String sessionToken,
            @Valid @RequestBody SubmitOrderRequest request) {

        SelfServiceOrder order = orderService.submitOrder(sessionToken, request);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("orderId", order.getOrder().getId());
        response.put("orderNumber", order.getOrder().getOrderNumber());
        response.put("estimatedReadyTime", order.getEstimatedReadyTime());
        response.put("status", order.getOrder().getStatus().name());

        return ResponseEntity.ok(response);
    }

    /**
     * Get order status.
     */
    @GetMapping("/order/{orderId}/status")
    @Operation(summary = "Get order status", description = "Get the status of an order")
    public ResponseEntity<Map<String, Object>> getOrderStatus(
            @PathVariable Long orderId) {

        SelfServiceOrder order = orderService.getOrderStatus(orderId);

        Map<String, Object> response = new HashMap<>();
        response.put("orderId", order.getOrder().getId());
        response.put("orderNumber", order.getOrder().getOrderNumber());
        response.put("status", order.getOrder().getStatus().name());
        response.put("orderType", order.getOrderType().name());
        response.put("estimatedReadyTime", order.getEstimatedReadyTime());
        response.put("actualReadyTime", order.getActualReadyTime());
        response.put("pickedUpAt", order.getPickedUpAt());
        response.put("total", order.getOrder().getTotal());

        return ResponseEntity.ok(response);
    }

    // ==================== Helpers ====================

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
