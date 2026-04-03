package com.elcafe.modules.inventory.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.inventory.dto.BatchRequest;
import com.elcafe.modules.inventory.dto.BatchResponse;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryBatch;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.inventory.repository.InventoryBatchRepository;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.service.InventoryBatchService;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.security.UserPrincipal;
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
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryBatchControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock private InventoryBatchService batchService;
    @Mock private InventoryBatchRepository batchRepository;
    @Mock private InventoryIngredientRepository ingredientRepository;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @InjectMocks private InventoryBatchController controller;

    private Restaurant restaurant;
    private Ingredient ingredient;
    private InventoryBatch batch;

    @BeforeEach
    void setUp() {
        UserPrincipal testUser = new UserPrincipal(1L, "admin@test.com", "pass", UserRole.ADMIN, true, 1L);
        HandlerMethodArgumentResolver authResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }
            @Override
            public Object resolveArgument(MethodParameter parameter, org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                    org.springframework.web.context.request.NativeWebRequest webRequest, org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                return testUser;
            }
        };
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setCustomArgumentResolvers(authResolver).build();

        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");

        ingredient = Ingredient.builder()
                .id(1L).name("Flour").unit("kg").currentStock(new BigDecimal("100"))
                .expiryAlertDays(7).build();
        ingredient.setRestaurant(restaurant);

        batch = InventoryBatch.builder()
                .id(1L).batchNumber("BATCH-001").quantity(new BigDecimal("50"))
                .initialQuantity(new BigDecimal("50")).ingredient(ingredient)
                .receivedDate(LocalDate.now()).expiryDate(LocalDate.now().plusDays(30))
                .costPerUnit(new BigDecimal("5000"))
                .status(InventoryBatch.Status.ACTIVE).build();
    }

    @Test @DisplayName("POST / — creates batch")
    void createBatch() throws Exception {
        BatchRequest request = BatchRequest.builder()
                .ingredientId(1L).quantity(new BigDecimal("50"))
                .receivedDate(LocalDate.now()).build();
        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
        when(batchService.createBatch(any(BatchRequest.class))).thenReturn(batch);

        mockMvc.perform(post("/api/v1/inventory/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test @DisplayName("GET /ingredient/{id} — returns batches for ingredient")
    void getBatchesByIngredient() throws Exception {
        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
        BatchResponse response = BatchResponse.builder().id(1L).batchNumber("BATCH-001").build();
        when(batchService.getBatchesByIngredient(1L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/inventory/batches/ingredient/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test @DisplayName("GET /expiring — returns expiring batches")
    void getExpiringBatches() throws Exception {
        when(batchService.getExpiringBatches(1L, 7)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/inventory/batches/expiring").param("restaurantId", "1"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /expired — returns expired batches")
    void getExpiredBatches() throws Exception {
        when(batchService.getExpiredBatches(1L)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/inventory/batches/expired").param("restaurantId", "1"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /summary — returns expiry summary")
    void getSummary() throws Exception {
        when(batchService.getExpirySummary(1L, 7))
                .thenReturn(new InventoryBatchService.ExpirySummary(2, 3, 7));
        mockMvc.perform(get("/api/v1/inventory/batches/summary").param("restaurantId", "1"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("PATCH /{batchId}/expiry — updates expiry date")
    void updateExpiry() throws Exception {
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        when(batchService.updateBatchExpiry(eq(1L), any(LocalDate.class))).thenReturn(batch);

        mockMvc.perform(patch("/api/v1/inventory/batches/1/expiry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiryDate\":\"2026-06-01\"}"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("POST /{batchId}/write-off — writes off batch")
    void writeOff() throws Exception {
        when(batchRepository.findById(1L)).thenReturn(Optional.of(batch));
        doNothing().when(batchService).writeOffBatch(anyLong(), anyString(), anyString());

        mockMvc.perform(post("/api/v1/inventory/batches/1/write-off")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Damaged\"}"))
                .andExpect(status().isOk());
        verify(batchService).writeOffBatch(eq(1L), eq("Damaged"), anyString());
    }

    @Test @DisplayName("POST /mark-expired — marks expired batches")
    void markExpired() throws Exception {
        when(batchService.markExpiredBatches(1L)).thenReturn(3);
        mockMvc.perform(post("/api/v1/inventory/batches/mark-expired").param("restaurantId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(3));
    }
}
