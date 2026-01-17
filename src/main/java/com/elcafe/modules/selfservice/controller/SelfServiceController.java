package com.elcafe.modules.selfservice.controller;

import com.elcafe.modules.bundle.entity.Bundle;
import com.elcafe.modules.bundle.repository.BundleRepository;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.promotion.entity.HappyHour;
import com.elcafe.modules.promotion.entity.Promotion;
import com.elcafe.modules.promotion.repository.HappyHourRepository;
import com.elcafe.modules.promotion.repository.PromotionRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    private final PromotionRepository promotionRepository;
    private final HappyHourRepository happyHourRepository;
    private final BundleRepository bundleRepository;

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

        List<Category> categories = categoryRepository.findByRestaurantIdAndActiveTrueOrderBySortOrder(restaurantId);

        List<Map<String, Object>> response = categories.stream().map(cat -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", cat.getId());
            map.put("name", cat.getName());
            map.put("imageUrl", cat.getImageUrl());
            map.put("sortOrder", cat.getSortOrder());
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
            // Filter by both categoryId AND restaurantId to ensure category belongs to the restaurant
            products = productRepository.findByCategoryIdAndRestaurantIdAndStatus(categoryId, restaurantId, ProductStatus.LIVE);
        } else {
            products = productRepository.findByRestaurantIdAndStatus(restaurantId, ProductStatus.LIVE);
        }

        List<Map<String, Object>> response = products.stream()
                .filter(Product::getInStock)
                .map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId());
            map.put("name", p.getName());
            map.put("description", p.getDescription());
            map.put("price", p.getPrice());
            map.put("imageUrl", p.getImageUrl());
            map.put("categoryId", p.getCategory().getId());
            map.put("categoryName", p.getCategory().getName());
            map.put("inStock", p.getInStock());
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
        response.put("description", product.getDescription());
        response.put("price", product.getPrice());
        response.put("imageUrl", product.getImageUrl());
        response.put("inStock", product.getInStock());

        // Variants
        if (product.getVariants() != null && !product.getVariants().isEmpty()) {
            response.put("variants", product.getVariants().stream().map(v -> {
                Map<String, Object> vmap = new HashMap<>();
                vmap.put("id", v.getId());
                vmap.put("name", v.getName());
                vmap.put("price", v.getPrice());
                vmap.put("inStock", v.getInStock());
                return vmap;
            }).toList());
        }

        // Linked items (modifiers)
        if (product.getLinkedItems() != null && !product.getLinkedItems().isEmpty()) {
            response.put("modifiers", product.getLinkedItems().stream()
                    .filter(li -> li.getLinkedProduct().getInStock())
                    .map(li -> {
                        Map<String, Object> lmap = new HashMap<>();
                        lmap.put("id", li.getId());
                        lmap.put("name", li.getLinkedProduct().getName());
                        lmap.put("price", li.getLinkedProduct().getPrice());
                        lmap.put("linkType", li.getLinkType().name());
                        return lmap;
                    }).toList());
        }

        return ResponseEntity.ok(response);
    }

    // ==================== Promotions & Offers ====================

    /**
     * Get active promotions for customer menu.
     */
    @GetMapping("/promotions/{restaurantId}")
    @Operation(summary = "Get active promotions", description = "Get active promotions for the customer menu")
    public ResponseEntity<List<Map<String, Object>>> getActivePromotions(
            @PathVariable Long restaurantId) {

        LocalDateTime now = LocalDateTime.now();
        List<Promotion> promotions = promotionRepository.findActivePromotions(restaurantId, now);

        List<Map<String, Object>> response = promotions.stream().map(promo -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", promo.getId());
            map.put("name", promo.getName());
            map.put("description", promo.getDescription());
            map.put("type", promo.getPromotionType() != null ? promo.getPromotionType().name() : null);
            map.put("discountValue", promo.getDiscountValue());
            map.put("minOrderAmount", promo.getRule() != null ? promo.getRule().getMinOrderAmount() : null);
            return map;
        }).toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Get active happy hour.
     */
    @GetMapping("/happy-hour/{restaurantId}")
    @Operation(summary = "Get active happy hour", description = "Get currently active happy hour if any")
    public ResponseEntity<Map<String, Object>> getActiveHappyHour(
            @PathVariable Long restaurantId) {

        List<HappyHour> happyHours = happyHourRepository.findByRestaurantIdAndActiveTrue(restaurantId);

        // Find currently active happy hour using the entity's method
        HappyHour active = happyHours.stream()
                .filter(HappyHour::isCurrentlyActive)
                .max((a, b) -> Integer.compare(
                        a.getPriority() != null ? a.getPriority() : 0,
                        b.getPriority() != null ? b.getPriority() : 0))
                .orElse(null);

        if (active == null) {
            return ResponseEntity.ok(null);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", active.getId());
        response.put("name", active.getName());
        response.put("discountPercent", active.getDiscountPercent());
        response.put("description", active.getDescription());
        response.put("priority", active.getPriority());
        // Include schedule info for the current day
        if (active.getSchedules() != null && !active.getSchedules().isEmpty()) {
            var todaySchedule = active.getSchedules().stream()
                    .filter(s -> s.getDayOfWeek().equals(getTodayDayString()))
                    .findFirst()
                    .orElse(null);
            if (todaySchedule != null) {
                response.put("startTime", todaySchedule.getStartTime());
                response.put("endTime", todaySchedule.getEndTime());
            }
        }

        return ResponseEntity.ok(response);
    }

    private String getTodayDayString() {
        return switch (java.time.LocalDate.now().getDayOfWeek()) {
            case MONDAY -> "MON";
            case TUESDAY -> "TUE";
            case WEDNESDAY -> "WED";
            case THURSDAY -> "THU";
            case FRIDAY -> "FRI";
            case SATURDAY -> "SAT";
            case SUNDAY -> "SUN";
        };
    }

    /**
     * Get menu bundles/combos.
     */
    @GetMapping("/bundles/{restaurantId}")
    @Operation(summary = "Get menu bundles", description = "Get active bundles/combos for the menu")
    public ResponseEntity<List<Map<String, Object>>> getMenuBundles(
            @PathVariable Long restaurantId) {

        List<Bundle> bundles = bundleRepository.findByRestaurantIdAndActiveTrue(restaurantId).stream()
                .filter(Bundle::isCurrentlyAvailable)
                .toList();

        List<Map<String, Object>> response = bundles.stream().map(bundle -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", bundle.getId());
            map.put("name", bundle.getName());
            map.put("description", bundle.getDescription());
            map.put("bundlePrice", bundle.getBundlePrice());
            map.put("originalPrice", bundle.getOriginalPrice());
            map.put("savings", bundle.getSavingsAmount());
            map.put("imageUrl", bundle.getImageUrl());
            return map;
        }).toList();

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
                .reduce(BigDecimal.ZERO, BigDecimal::add);

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
