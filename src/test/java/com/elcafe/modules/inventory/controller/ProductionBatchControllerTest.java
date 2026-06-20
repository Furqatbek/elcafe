package com.elcafe.modules.inventory.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.inventory.dto.AddInputRequest;
import com.elcafe.modules.inventory.dto.CompleteBatchRequest;
import com.elcafe.modules.inventory.dto.CreateProductionBatchRequest;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.ProductionBatch;
import com.elcafe.modules.inventory.entity.ProductionBatchInput;
import com.elcafe.modules.inventory.service.ProductionBatchService;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.restaurant.entity.Restaurant;
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
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductionBatchControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock private ProductionBatchService productionBatchService;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @InjectMocks private ProductionBatchController controller;

    private ProductionBatch batch;
    private Restaurant restaurant;
    private Product product;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");

        product = new Product();
        product.setId(10L);
        product.setName("Shurva");

        batch = ProductionBatch.builder()
                .id(100L).restaurant(restaurant).product(product)
                .batchNumber("PB-TEST-001").name("Shurva Morning")
                .outputUnit("L").outputQuantity(new BigDecimal("10"))
                .remainingQuantity(new BigDecimal("8"))
                .totalInputCost(new BigDecimal("300000"))
                .costPerUnit(new BigDecimal("30000"))
                .status(ProductionBatch.Status.READY).version(0L)
                .inputs(new ArrayList<>()).consumptions(new ArrayList<>())
                .build();
    }

    @Test @DisplayName("POST / — creates batch")
    void createBatch() throws Exception {
        when(productionBatchService.createBatch(any())).thenReturn(batch);

        CreateProductionBatchRequest request = CreateProductionBatchRequest.builder()
                .restaurantId(1L).productId(10L).name("Shurva Morning").outputUnit("L").build();

        mockMvc.perform(post("/api/v1/inventory/production-batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Shurva Morning"));
    }

    @Test @DisplayName("GET /restaurant/{id} — lists batches")
    void getBatches() throws Exception {
        when(productionBatchService.getBatchesByRestaurant(1L)).thenReturn(List.of(batch));

        mockMvc.perform(get("/api/v1/inventory/production-batches/restaurant/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("Shurva Morning"));
    }

    @Test @DisplayName("GET /restaurant/{id}?status=READY — filters by status")
    void getBatchesByStatus() throws Exception {
        when(productionBatchService.getBatchesByStatus(1L, ProductionBatch.Status.READY))
                .thenReturn(List.of(batch));

        mockMvc.perform(get("/api/v1/inventory/production-batches/restaurant/1")
                        .param("status", "READY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test @DisplayName("GET /{id} — returns batch details")
    void getBatch() throws Exception {
        when(productionBatchService.getBatchById(100L)).thenReturn(batch);

        mockMvc.perform(get("/api/v1/inventory/production-batches/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batchNumber").value("PB-TEST-001"));
    }

    @Test @DisplayName("POST /{id}/inputs — adds input")
    void addInput() throws Exception {
        Ingredient meat = Ingredient.builder().id(1L).name("Meat").unit("kg").build();
        ProductionBatchInput input = ProductionBatchInput.builder()
                .id(200L).productionBatch(batch).ingredient(meat)
                .actualQuantity(new BigDecimal("3")).unit("kg")
                .costPerUnit(new BigDecimal("78000")).totalCost(new BigDecimal("234000")).build();
        when(productionBatchService.addInput(eq(100L), any())).thenReturn(input);

        AddInputRequest request = AddInputRequest.builder()
                .ingredientId(1L).actualQuantity(new BigDecimal("3")).unit("kg").build();

        mockMvc.perform(post("/api/v1/inventory/production-batches/100/inputs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.ingredientName").value("Meat"));
    }

    @Test @DisplayName("PUT /{id}/inputs/{inputId} — updates input")
    void updateInput() throws Exception {
        Ingredient meat = Ingredient.builder().id(1L).name("Meat").unit("kg").build();
        ProductionBatchInput input = ProductionBatchInput.builder()
                .id(200L).productionBatch(batch).ingredient(meat)
                .actualQuantity(new BigDecimal("4")).unit("kg")
                .costPerUnit(new BigDecimal("78000")).totalCost(new BigDecimal("312000")).build();
        when(productionBatchService.updateInput(eq(100L), eq(200L), any())).thenReturn(input);

        AddInputRequest request = AddInputRequest.builder()
                .ingredientId(1L).actualQuantity(new BigDecimal("4")).unit("kg").build();

        mockMvc.perform(put("/api/v1/inventory/production-batches/100/inputs/200")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actualQuantity").value(4));
    }

    @Test @DisplayName("POST /{id}/start — starts batch")
    void startBatch() throws Exception {
        batch.setStatus(ProductionBatch.Status.IN_PROGRESS);
        when(productionBatchService.startBatch(100L)).thenReturn(batch);

        mockMvc.perform(post("/api/v1/inventory/production-batches/100/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
    }

    @Test @DisplayName("POST /{id}/complete — completes batch")
    void completeBatch() throws Exception {
        when(productionBatchService.completeBatch(eq(100L), any())).thenReturn(batch);

        CompleteBatchRequest request = CompleteBatchRequest.builder()
                .outputQuantity(new BigDecimal("8.5")).outputUnit("L").build();

        mockMvc.perform(post("/api/v1/inventory/production-batches/100/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.costPerUnit").value(30000));
    }

    @Test @DisplayName("POST /{id}/waste — records waste")
    void recordWaste() throws Exception {
        mockMvc.perform(post("/api/v1/inventory/production-batches/100/waste")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\": 2, \"reason\": \"End of day\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(productionBatchService).recordWaste(eq(100L), any(), eq("End of day"));
    }

    @Test @DisplayName("GET /restaurant/{id}/available — lists available batches")
    void getAvailableBatches() throws Exception {
        when(productionBatchService.getActiveBatches(1L)).thenReturn(List.of(batch));

        mockMvc.perform(get("/api/v1/inventory/production-batches/restaurant/1/available"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test @DisplayName("DELETE /{id} — deletes draft batch")
    void deleteBatch() throws Exception {
        mockMvc.perform(delete("/api/v1/inventory/production-batches/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(productionBatchService).deleteBatch(100L);
    }

    @Test @DisplayName("POST / — validation error when name is blank")
    void createBatchValidationError() throws Exception {
        CreateProductionBatchRequest request = CreateProductionBatchRequest.builder()
                .restaurantId(1L).name("").outputUnit("L").build();

        mockMvc.perform(post("/api/v1/inventory/production-batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
