package com.elcafe.modules.menu.controller;

import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.service.MenuService;
import com.elcafe.utils.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/menu")
@RequiredArgsConstructor
@Tag(name = "Menu", description = "Menu management and public access endpoints")
public class MenuController {

    private final MenuService menuService;

    @GetMapping("/public/{restaurantId}")
    @Operation(summary = "Get public menu", description = "Get public menu for a restaurant (cached)")
    public ResponseEntity<ApiResponse<List<Category>>> getPublicMenu(@PathVariable Long restaurantId) {
        List<Category> menu = menuService.getPublicMenu(restaurantId);
        return ResponseEntity.ok(ApiResponse.success(menu));
    }

    @GetMapping("/restaurants/{restaurantId}/categories")
    @Operation(summary = "Get all categories", description = "Get all categories for a restaurant")
    public ResponseEntity<ApiResponse<List<Category>>> getCategories(@PathVariable Long restaurantId) {
        List<Category> categories = menuService.getCategoriesByRestaurant(restaurantId);
        return ResponseEntity.ok(ApiResponse.success(categories));
    }
}
