package com.elcafe.modules.inventory.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.inventory.dto.StockCountRequest;
import com.elcafe.modules.inventory.dto.StockCountResponse;
import com.elcafe.modules.inventory.dto.VarianceReportResponse;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.StockCount;
import com.elcafe.modules.inventory.entity.StockCountItem;
import com.elcafe.modules.inventory.service.StockCountService;
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

import com.elcafe.modules.restaurant.entity.Restaurant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockCountControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock private StockCountService stockCountService;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @InjectMocks private StockCountController controller;

    private StockCount stockCount;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        Restaurant restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");

        stockCount = new StockCount();
        stockCount.setId(1L);
        stockCount.setRestaurant(restaurant);
        stockCount.setCountNumber("SC-001");
        stockCount.setCountType(StockCount.CountType.FULL);
        stockCount.setStatus(StockCount.Status.DRAFT);
        stockCount.setTotalItems(0);
        stockCount.setCountedItems(0);
        stockCount.setVarianceCount(0);
        stockCount.setTotalVarianceValue(BigDecimal.ZERO);
        stockCount.setItems(new ArrayList<>());
    }

    @Test @DisplayName("GET / — lists stock counts")
    void getStockCounts() throws Exception {
        when(stockCountService.getStockCountsByRestaurant(1L)).thenReturn(List.of(stockCount));
        mockMvc.perform(get("/api/v1/inventory/stock-counts").param("restaurantId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test @DisplayName("GET /active — lists active stock counts")
    void getActive() throws Exception {
        when(stockCountService.getActiveStockCounts(1L)).thenReturn(List.of(stockCount));
        mockMvc.perform(get("/api/v1/inventory/stock-counts/active").param("restaurantId", "1"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /{id} — returns single stock count")
    void getById() throws Exception {
        when(stockCountService.getStockCountById(1L)).thenReturn(stockCount);
        mockMvc.perform(get("/api/v1/inventory/stock-counts/1"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("POST / — creates stock count")
    void create() throws Exception {
        StockCountRequest request = new StockCountRequest();
        request.setRestaurantId(1L);
        request.setCountType(StockCount.CountType.FULL);
        request.setInitiatedBy("admin");
        when(stockCountService.createStockCount(any(StockCountRequest.class))).thenReturn(stockCount);

        mockMvc.perform(post("/api/v1/inventory/stock-counts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test @DisplayName("POST /{id}/start — starts counting")
    void start() throws Exception {
        stockCount.setStatus(StockCount.Status.IN_PROGRESS);
        when(stockCountService.startStockCount(1L, "counter1")).thenReturn(stockCount);
        mockMvc.perform(post("/api/v1/inventory/stock-counts/1/start").param("countedBy", "counter1"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("POST /items/record-count — records item count")
    void recordCount() throws Exception {
        StockCountRequest.RecordCountRequest request = new StockCountRequest.RecordCountRequest();
        request.setItemId(1L);
        request.setCountedQuantity(new BigDecimal("95"));
        request.setCountedBy("counter1");

        Ingredient ingredient = Ingredient.builder().id(1L).name("Flour").unit("kg")
                .currentStock(new BigDecimal("100")).build();
        StockCountItem item = new StockCountItem();
        item.setId(1L);
        item.setIngredient(ingredient);
        item.setSystemQuantity(new BigDecimal("100"));
        item.setCountedQuantity(new BigDecimal("95"));
        item.setVarianceQuantity(new BigDecimal("-5"));
        item.setStatus(StockCountItem.Status.COUNTED);

        when(stockCountService.recordCount(any(StockCountRequest.RecordCountRequest.class))).thenReturn(item);

        mockMvc.perform(post("/api/v1/inventory/stock-counts/items/record-count")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("POST /items/variance-reason — sets variance reason")
    void setVarianceReason() throws Exception {
        StockCountRequest.VarianceReasonRequest request = new StockCountRequest.VarianceReasonRequest();
        request.setItemId(1L);
        request.setVarianceReason(StockCountItem.VarianceReason.SHRINKAGE);

        Ingredient ingredient = Ingredient.builder().id(1L).name("Flour").unit("kg")
                .currentStock(new BigDecimal("100")).build();
        StockCountItem item = new StockCountItem();
        item.setId(1L);
        item.setIngredient(ingredient);
        item.setStatus(StockCountItem.Status.COUNTED);

        when(stockCountService.setVarianceReason(any(StockCountRequest.VarianceReasonRequest.class)))
                .thenReturn(item);

        mockMvc.perform(post("/api/v1/inventory/stock-counts/items/variance-reason")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("POST /{id}/submit-review — submits for review")
    void submitReview() throws Exception {
        when(stockCountService.submitForReview(1L, "reviewer1")).thenReturn(stockCount);
        mockMvc.perform(post("/api/v1/inventory/stock-counts/1/submit-review")
                        .param("reviewedBy", "reviewer1"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("POST /{id}/approve — approves stock count")
    void approve() throws Exception {
        StockCountRequest.ApproveRequest request = new StockCountRequest.ApproveRequest();
        request.setApprovedBy("manager");
        request.setAdjustInventory(true);
        when(stockCountService.approveStockCount(eq(1L), any(StockCountRequest.ApproveRequest.class)))
                .thenReturn(stockCount);

        mockMvc.perform(post("/api/v1/inventory/stock-counts/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("POST /{id}/cancel — cancels stock count")
    void cancel() throws Exception {
        when(stockCountService.cancelStockCount(1L, "reason", "admin")).thenReturn(stockCount);
        mockMvc.perform(post("/api/v1/inventory/stock-counts/1/cancel")
                        .param("reason", "reason").param("cancelledBy", "admin"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /{id}/variances — returns items with variance")
    void getVariances() throws Exception {
        Ingredient ingredient = Ingredient.builder().id(1L).name("Flour").unit("kg")
                .currentStock(new BigDecimal("100")).build();
        StockCountItem item = new StockCountItem();
        item.setId(1L);
        item.setIngredient(ingredient);
        item.setVarianceQuantity(new BigDecimal("-5"));
        when(stockCountService.getItemsWithVariance(1L)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/inventory/stock-counts/1/variances"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /variance-report — generates variance report")
    void getVarianceReport() throws Exception {
        VarianceReportResponse report = VarianceReportResponse.builder().build();
        when(stockCountService.generateVarianceReport(eq(1L), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(report);
        mockMvc.perform(get("/api/v1/inventory/stock-counts/variance-report")
                        .param("restaurantId", "1")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-03-31"))
                .andExpect(status().isOk());
    }
}
