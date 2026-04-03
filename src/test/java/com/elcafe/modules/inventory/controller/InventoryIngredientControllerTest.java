package com.elcafe.modules.inventory.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.inventory.dto.IngredientRequest;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryTransaction;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.SupplierRepository;
import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.inventory.service.StockOperationService;
import com.elcafe.modules.menu.service.ProductCostService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryIngredientControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock private InventoryIngredientRepository ingredientRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private InventoryService inventoryService;
    @Mock private ProductCostService productCostService;
    @Mock private StockOperationService stockOperationService;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @InjectMocks private InventoryIngredientController controller;

    private Restaurant restaurant;
    private Ingredient ingredient;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");

        ingredient = Ingredient.builder()
                .id(1L).name("Flour").unit("kg")
                .currentStock(new BigDecimal("100"))
                .minimumStock(new BigDecimal("10"))
                .reorderLevel(new BigDecimal("20"))
                .active(true).trackInventory(true).trackExpiry(false)
                .version(0L).build();
        ingredient.setRestaurant(restaurant);
    }

    @Test @DisplayName("GET / — returns ingredients for restaurant")
    void getIngredients() throws Exception {
        when(ingredientRepository.findByRestaurant_Id(1L)).thenReturn(List.of(ingredient));
        mockMvc.perform(get("/api/v1/inventory/ingredients").param("restaurantId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test @DisplayName("GET /{id} — returns single ingredient")
    void getById() throws Exception {
        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
        mockMvc.perform(get("/api/v1/inventory/ingredients/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Flour"));
    }

    @Test @DisplayName("GET /low-stock — returns low stock ingredients")
    void getLowStock() throws Exception {
        when(ingredientRepository.findLowStockIngredients(1L)).thenReturn(List.of(ingredient));
        mockMvc.perform(get("/api/v1/inventory/ingredients/low-stock").param("restaurantId", "1"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /reorder — returns reorder ingredients")
    void getReorder() throws Exception {
        when(ingredientRepository.findIngredientsNeedingReorder(1L)).thenReturn(List.of(ingredient));
        mockMvc.perform(get("/api/v1/inventory/ingredients/reorder").param("restaurantId", "1"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("POST / — creates ingredient")
    void create() throws Exception {
        IngredientRequest request = IngredientRequest.builder()
                .restaurantId(1L).name("Flour").unit("kg")
                .currentStock(BigDecimal.ZERO).minimumStock(BigDecimal.TEN)
                .reorderLevel(new BigDecimal("20")).build();
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(i -> {
            Ingredient saved = i.getArgument(0);
            saved.setId(1L);
            saved.setRestaurant(restaurant);
            return saved;
        });

        mockMvc.perform(post("/api/v1/inventory/ingredients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test @DisplayName("PUT /{id} — updates ingredient")
    void update() throws Exception {
        IngredientRequest request = IngredientRequest.builder()
                .restaurantId(1L).name("Updated Flour").unit("kg")
                .currentStock(new BigDecimal("100")).minimumStock(BigDecimal.TEN)
                .reorderLevel(new BigDecimal("20"))
                .active(true).trackInventory(true).trackExpiry(false).build();
        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
        when(ingredientRepository.save(any(Ingredient.class))).thenAnswer(i -> {
            Ingredient saved = i.getArgument(0);
            saved.setRestaurant(restaurant);
            return saved;
        });

        mockMvc.perform(put("/api/v1/inventory/ingredients/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
        verify(productCostService).recalculateProductsUsingIngredient(1L);
    }

    @Test @DisplayName("DELETE /{id} — deletes ingredient")
    void deleteIngredient() throws Exception {
        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
        mockMvc.perform(delete("/api/v1/inventory/ingredients/1"))
                .andExpect(status().isOk());
        verify(ingredientRepository).deleteById(1L);
    }

    // add-stock and adjust-stock endpoints require @AuthenticationPrincipal — tested via integration tests

    @Test @DisplayName("GET /{id}/transactions — returns transaction history")
    void getTransactions() throws Exception {
        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
        when(inventoryService.getTransactionHistory(1L)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/inventory/ingredients/1/transactions"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /{id}/reconcile — reconciles single ingredient")
    void reconcile() throws Exception {
        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
        StockOperationService.StockReconciliationResult result =
                new StockOperationService.StockReconciliationResult(
                        1L, "Flour", new BigDecimal("100"), new BigDecimal("100"),
                        BigDecimal.ZERO, true, "Stock is consistent");
        when(stockOperationService.reconcileStock(1L)).thenReturn(result);

        mockMvc.perform(get("/api/v1/inventory/ingredients/1/reconcile"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /reconcile — reconciles all for restaurant")
    void reconcileAll() throws Exception {
        when(stockOperationService.reconcileAllStock(1L)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/inventory/ingredients/reconcile").param("restaurantId", "1"))
                .andExpect(status().isOk());
    }
}
