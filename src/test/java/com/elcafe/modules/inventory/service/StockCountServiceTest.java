package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.dto.StockCountRequest;
import com.elcafe.modules.inventory.dto.VarianceReportResponse;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.StockCount;
import com.elcafe.modules.inventory.entity.StockCountItem;
import com.elcafe.modules.inventory.entity.StockVarianceHistory;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.StockCountItemRepository;
import com.elcafe.modules.inventory.repository.StockCountRepository;
import com.elcafe.modules.inventory.repository.StockVarianceHistoryRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockCountServiceTest {

    @Mock private StockCountRepository stockCountRepository;
    @Mock private StockCountItemRepository stockCountItemRepository;
    @Mock private StockVarianceHistoryRepository varianceHistoryRepository;
    @Mock private InventoryIngredientRepository ingredientRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private InventoryService inventoryService;
    @InjectMocks private StockCountService stockCountService;

    private Restaurant restaurant;
    private Ingredient flour;
    private StockCount stockCount;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test");

        flour = Ingredient.builder()
                .id(1L).name("Flour").unit("kg")
                .currentStock(new BigDecimal("100"))
                .active(true).version(0L).build();

        stockCount = StockCount.builder()
                .id(1L).countNumber("SC-20260403-0001")
                .restaurant(restaurant)
                .countType(StockCount.CountType.FULL)
                .status(StockCount.Status.DRAFT)
                .totalItems(0).countedItems(0).varianceCount(0)
                .totalVarianceValue(BigDecimal.ZERO)
                .notes("")
                .build();
        stockCount.setItems(new ArrayList<>());
    }

    @Nested @DisplayName("createStockCount")
    class CreateTests {
        @Test @DisplayName("FULL type — includes all active ingredients")
        void fullType() {
            StockCountRequest request = new StockCountRequest();
            request.setRestaurantId(1L);
            request.setCountType(StockCount.CountType.FULL);
            request.setInitiatedBy("admin");

            when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
            when(ingredientRepository.findByRestaurant_IdAndActiveTrue(1L)).thenReturn(List.of(flour));
            when(stockCountRepository.save(any(StockCount.class))).thenAnswer(i -> { StockCount sc = i.getArgument(0); sc.setId(1L); return sc; });
            when(stockCountRepository.countByRestaurantIdAndCountNumberPrefix(anyLong(), anyString())).thenReturn(0L);

            StockCount result = stockCountService.createStockCount(request);

            assertThat(result.getTotalItems()).isEqualTo(1);
            assertThat(result.getCountType()).isEqualTo(StockCount.CountType.FULL);
        }

        @Test @DisplayName("CYCLE type — includes specific ingredients")
        void cycleType() {
            StockCountRequest request = new StockCountRequest();
            request.setRestaurantId(1L);
            request.setCountType(StockCount.CountType.CYCLE);
            request.setIngredientIds(List.of(1L));
            request.setInitiatedBy("admin");

            when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
            when(ingredientRepository.findAllById(List.of(1L))).thenReturn(List.of(flour));
            when(stockCountRepository.save(any(StockCount.class))).thenAnswer(i -> { StockCount sc = i.getArgument(0); sc.setId(1L); return sc; });
            when(stockCountRepository.countByRestaurantIdAndCountNumberPrefix(anyLong(), anyString())).thenReturn(0L);

            StockCount result = stockCountService.createStockCount(request);

            assertThat(result.getTotalItems()).isEqualTo(1);
        }
    }

    @Test @DisplayName("startStockCount — sets IN_PROGRESS")
    void start() {
        when(stockCountRepository.findById(1L)).thenReturn(Optional.of(stockCount));
        when(stockCountRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        StockCount result = stockCountService.startStockCount(1L, "counter1");

        assertThat(result.getStatus()).isEqualTo(StockCount.Status.IN_PROGRESS);
        assertThat(result.getCountedBy()).isEqualTo("counter1");
    }

    @Test @DisplayName("startStockCount — rejects non-draft")
    void startNonDraft() {
        stockCount.setStatus(StockCount.Status.IN_PROGRESS);
        when(stockCountRepository.findById(1L)).thenReturn(Optional.of(stockCount));

        assertThatThrownBy(() -> stockCountService.startStockCount(1L, "counter"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("draft");
    }

    @Test @DisplayName("recordCount — records and calculates variance")
    void recordCount() {
        StockCountItem item = StockCountItem.builder()
                .id(1L).ingredient(flour)
                .systemQuantity(new BigDecimal("100"))
                .status(StockCountItem.Status.PENDING).build();
        item.setStockCount(stockCount);
        stockCount.setStatus(StockCount.Status.IN_PROGRESS);
        stockCount.setItems(new ArrayList<>(List.of(item)));

        StockCountRequest.RecordCountRequest request = new StockCountRequest.RecordCountRequest();
        request.setItemId(1L);
        request.setCountedQuantity(new BigDecimal("95"));
        request.setCountedBy("counter1");

        when(stockCountItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(stockCountItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(stockCountRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        StockCountItem result = stockCountService.recordCount(request);

        assertThat(result.getCountedQuantity()).isEqualByComparingTo("95");
        assertThat(result.getStatus()).isEqualTo(StockCountItem.Status.COUNTED);
    }

    @Test @DisplayName("setVarianceReason — sets reason on item")
    void setVarianceReason() {
        StockCountItem item = StockCountItem.builder()
                .id(1L).ingredient(flour).status(StockCountItem.Status.COUNTED).build();

        StockCountRequest.VarianceReasonRequest request = new StockCountRequest.VarianceReasonRequest();
        request.setItemId(1L);
        request.setVarianceReason(StockCountItem.VarianceReason.SHRINKAGE);

        when(stockCountItemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(stockCountItemRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        StockCountItem result = stockCountService.setVarianceReason(request);

        assertThat(result.getVarianceReason()).isEqualTo(StockCountItem.VarianceReason.SHRINKAGE);
    }

    @Test @DisplayName("submitForReview — requires all items counted")
    void submitNotFullyCounted() {
        stockCount.setStatus(StockCount.Status.IN_PROGRESS);
        StockCountItem uncounted = StockCountItem.builder()
                .id(1L).ingredient(flour).status(StockCountItem.Status.PENDING).build();
        stockCount.setItems(new ArrayList<>(List.of(uncounted)));
        when(stockCountRepository.findById(1L)).thenReturn(Optional.of(stockCount));

        assertThatThrownBy(() -> stockCountService.submitForReview(1L, "reviewer"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("counted");
    }

    @Test @DisplayName("approveStockCount — with adjustment")
    void approveWithAdjustment() {
        stockCount.setStatus(StockCount.Status.PENDING_REVIEW);
        StockCountItem item = StockCountItem.builder()
                .id(1L).ingredient(flour)
                .systemQuantity(new BigDecimal("100"))
                .countedQuantity(new BigDecimal("95"))
                .varianceQuantity(new BigDecimal("-5"))
                .status(StockCountItem.Status.COUNTED).build();
        item.setStockCount(stockCount);
        stockCount.setItems(new ArrayList<>(List.of(item)));

        StockCountRequest.ApproveRequest request = new StockCountRequest.ApproveRequest();
        request.setApprovedBy("manager");
        request.setAdjustInventory(true);

        when(stockCountRepository.findById(1L)).thenReturn(Optional.of(stockCount));
        when(stockCountRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(varianceHistoryRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        StockCount result = stockCountService.approveStockCount(1L, request);

        assertThat(result.getStatus()).isEqualTo(StockCount.Status.APPROVED);
        verify(inventoryService).adjustStock(eq(1L), eq(new BigDecimal("95")), anyString(), eq("manager"));
        verify(varianceHistoryRepository).save(any(StockVarianceHistory.class));
    }

    @Test @DisplayName("approveStockCount — without adjustment")
    void approveWithoutAdjustment() {
        stockCount.setStatus(StockCount.Status.PENDING_REVIEW);
        stockCount.setItems(new ArrayList<>());

        StockCountRequest.ApproveRequest request = new StockCountRequest.ApproveRequest();
        request.setApprovedBy("manager");
        request.setAdjustInventory(false);

        when(stockCountRepository.findById(1L)).thenReturn(Optional.of(stockCount));
        when(stockCountRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        StockCount result = stockCountService.approveStockCount(1L, request);

        assertThat(result.getStatus()).isEqualTo(StockCount.Status.APPROVED);
        verify(inventoryService, never()).adjustStock(anyLong(), any(), anyString(), anyString());
    }

    @Test @DisplayName("cancelStockCount — rejects approved")
    void cancelApproved() {
        stockCount.setStatus(StockCount.Status.APPROVED);
        when(stockCountRepository.findById(1L)).thenReturn(Optional.of(stockCount));

        assertThatThrownBy(() -> stockCountService.cancelStockCount(1L, "reason", "admin"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Approved");
    }

    @Test @DisplayName("cancelStockCount — success")
    void cancel() {
        stockCount.setStatus(StockCount.Status.DRAFT);
        when(stockCountRepository.findById(1L)).thenReturn(Optional.of(stockCount));
        when(stockCountRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        StockCount result = stockCountService.cancelStockCount(1L, "No longer needed", "admin");

        assertThat(result.getStatus()).isEqualTo(StockCount.Status.CANCELLED);
    }

    @Nested @DisplayName("Query methods")
    class QueryTests {
        @Test @DisplayName("getStockCountsByRestaurant")
        void byRestaurant() {
            when(stockCountRepository.findByRestaurant_Id(1L)).thenReturn(List.of(stockCount));
            assertThat(stockCountService.getStockCountsByRestaurant(1L)).hasSize(1);
        }

        @Test @DisplayName("getActiveStockCounts")
        void active() {
            when(stockCountRepository.findActiveStockCounts(1L)).thenReturn(List.of(stockCount));
            assertThat(stockCountService.getActiveStockCounts(1L)).hasSize(1);
        }

        @Test @DisplayName("getItemsWithVariance")
        void withVariance() {
            when(stockCountItemRepository.findItemsWithVariance(1L)).thenReturn(List.of());
            assertThat(stockCountService.getItemsWithVariance(1L)).isEmpty();
        }
    }

    @Test @DisplayName("generateVarianceReport — builds report")
    void varianceReport() {
        when(varianceHistoryRepository.getTotalVarianceValueByDateRange(eq(1L), any(), any()))
                .thenReturn(new BigDecimal("50000"));
        when(varianceHistoryRepository.getVarianceBreakdownByReason(eq(1L), any(), any()))
                .thenReturn(List.of());
        when(varianceHistoryRepository.getTopVarianceIngredients(eq(1L), any(), any(), any()))
                .thenReturn(List.of());

        VarianceReportResponse report = stockCountService.generateVarianceReport(
                1L, LocalDate.now().minusDays(30), LocalDate.now());

        assertThat(report.getTotalVarianceValue()).isEqualByComparingTo("50000");
    }
}
