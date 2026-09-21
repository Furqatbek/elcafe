package com.elcafe.modules.inventory.controller;

import com.elcafe.modules.inventory.dto.RecipeRequest;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.ProductIngredient;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.menu.service.ProductCostService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RecipeControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock private InventoryProductIngredientRepository productIngredientRepository;
    @Mock private ProductRepository productRepository;
    @Mock private InventoryIngredientRepository ingredientRepository;
    @Mock private InventoryService inventoryService;
    @Mock private ProductCostService productCostService;
    @InjectMocks private RecipeController controller;

    private Product product;
    private Ingredient ingredient;
    private ProductIngredient recipe;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        product = new Product();
        product.setId(1L);
        product.setName("Steak");

        ingredient = Ingredient.builder()
                .id(1L).name("Beef").unit("kg")
                .currentStock(new BigDecimal("50")).build();

        recipe = ProductIngredient.builder()
                .id(1L).product(product).ingredient(ingredient)
                .quantityRequired(new BigDecimal("0.3")).unit("kg")
                .optional(false).build();
    }

    @Test @DisplayName("GET /product/{productId} — returns product recipe")
    void getProductRecipe() throws Exception {
        when(productIngredientRepository.findByProductIdWithIngredients(1L)).thenReturn(List.of(recipe));
        mockMvc.perform(get("/api/v1/inventory/recipes/product/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test @DisplayName("GET /ingredient/{ingredientId} — returns ingredient usage")
    void getIngredientUsage() throws Exception {
        when(productIngredientRepository.findByIngredientIdWithProductAndIngredient(1L))
                .thenReturn(List.of(recipe));
        mockMvc.perform(get("/api/v1/inventory/recipes/ingredient/1"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("POST / — creates recipe")
    void createRecipe() throws Exception {
        RecipeRequest request = RecipeRequest.builder()
                .productId(1L).ingredientId(1L)
                .quantityRequired(new BigDecimal("0.3")).unit("kg").build();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
        when(productIngredientRepository.save(any(ProductIngredient.class))).thenReturn(recipe);
        when(productCostService.recalculateProductCost(1L)).thenReturn(BigDecimal.valueOf(15000));

        mockMvc.perform(post("/api/v1/inventory/recipes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
        verify(productCostService).recalculateProductCost(1L);
    }

    @Test @DisplayName("PUT /{id} — updates recipe")
    void updateRecipe() throws Exception {
        RecipeRequest request = RecipeRequest.builder()
                .productId(1L).ingredientId(1L)
                .quantityRequired(new BigDecimal("0.5")).unit("kg").build();
        when(productIngredientRepository.findByIdWithProductAndIngredient(1L))
                .thenReturn(Optional.of(recipe));
        when(productIngredientRepository.save(any(ProductIngredient.class))).thenReturn(recipe);

        mockMvc.perform(put("/api/v1/inventory/recipes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("DELETE /{id} — deletes recipe")
    void deleteRecipe() throws Exception {
        when(productIngredientRepository.findByIdWithProductAndIngredient(1L))
                .thenReturn(Optional.of(recipe));
        mockMvc.perform(delete("/api/v1/inventory/recipes/1"))
                .andExpect(status().isOk());
        verify(productIngredientRepository).deleteById(1L);
        verify(productCostService).recalculateProductCost(1L);
    }

    @Test @DisplayName("GET /product/{productId}/check-availability — checks product availability")
    void checkAvailability() throws Exception {
        when(inventoryService.canMakeProduct(1L, 1)).thenReturn(true);
        mockMvc.perform(get("/api/v1/inventory/recipes/product/1/check-availability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test @DisplayName("POST /product/{productId}/recalculate-cost — recalculates product cost")
    void recalculateCost() throws Exception {
        when(productCostService.recalculateProductCost(1L)).thenReturn(new BigDecimal("25000"));
        mockMvc.perform(post("/api/v1/inventory/recipes/product/1/recalculate-cost"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("POST /recalculate-all-costs — recalculates all")
    void recalculateAll() throws Exception {
        when(productCostService.recalculateAllProductCosts()).thenReturn(15);
        mockMvc.perform(post("/api/v1/inventory/recipes/recalculate-all-costs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(15));
    }

    @Test @DisplayName("GET /product/{productId}/cost-breakdown — returns cost breakdown")
    void getCostBreakdown() throws Exception {
        when(productCostService.getCostBreakdown(1L)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/inventory/recipes/product/1/cost-breakdown"))
                .andExpect(status().isOk());
    }
}
