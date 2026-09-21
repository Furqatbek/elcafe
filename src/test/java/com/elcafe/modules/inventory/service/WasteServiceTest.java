package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.dto.WasteRecordRequest;
import com.elcafe.modules.inventory.dto.WasteReportResponse;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.WasteRecord;
import com.elcafe.modules.inventory.repository.InventoryBatchRepository;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.WasteRecordRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WasteServiceTest {

    @Mock private WasteRecordRepository wasteRecordRepository;
    @Mock private InventoryIngredientRepository ingredientRepository;
    @Mock private InventoryBatchRepository batchRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private InventoryService inventoryService;
    @InjectMocks private WasteService wasteService;

    private Restaurant restaurant;
    private Ingredient ingredient;
    private WasteRecord wasteRecord;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant(); restaurant.setId(1L); restaurant.setName("Test");
        ingredient = Ingredient.builder().id(1L).name("Flour").unit("kg")
                .currentStock(new BigDecimal("100")).costPerUnit(new BigDecimal("5000")).build();
        ingredient.setRestaurant(restaurant);
        wasteRecord = WasteRecord.builder().id(1L).restaurant(restaurant).ingredient(ingredient)
                .wasteDate(LocalDate.now()).quantity(new BigDecimal("5")).unitCost(new BigDecimal("5000"))
                .totalCost(new BigDecimal("25000")).wasteReason(WasteRecord.WasteReason.EXPIRED).build();
    }

    @Test @DisplayName("recordWaste — saves with cost from ingredient")
    void recordWaste_success() {
        WasteRecordRequest req = WasteRecordRequest.builder().restaurantId(1L).ingredientId(1L)
                .quantity(new BigDecimal("5")).wasteReason(WasteRecord.WasteReason.EXPIRED)
                .wasteDate(LocalDate.now()).recordedBy("admin").build();
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
        when(wasteRecordRepository.save(any())).thenAnswer(i -> { WasteRecord w = i.getArgument(0); w.setId(1L); return w; });

        WasteRecord result = wasteService.recordWaste(req);
        assertThat(result).isNotNull();
        verify(inventoryService).adjustStock(eq(1L), any(), anyString(), eq("admin"));
    }

    @Test @DisplayName("getWasteRecords — returns all for restaurant")
    void getWasteRecords_all() {
        when(wasteRecordRepository.findByRestaurant_Id(1L)).thenReturn(List.of(wasteRecord));
        assertThat(wasteService.getWasteRecords(1L)).hasSize(1);
    }

    @Test @DisplayName("getWasteRecords — filters by date range")
    void getWasteRecords_filtered() {
        when(wasteRecordRepository.findByRestaurant_IdAndDateRange(eq(1L), any(), any())).thenReturn(List.of(wasteRecord));
        assertThat(wasteService.getWasteRecords(1L, LocalDate.now().minusDays(7), LocalDate.now(), null, null)).hasSize(1);
    }

    @Test @DisplayName("getWasteRecordById — found")
    void getWasteRecordById_found() {
        when(wasteRecordRepository.findById(1L)).thenReturn(Optional.of(wasteRecord));
        assertThat(wasteService.getWasteRecordById(1L).getId()).isEqualTo(1L);
    }

    @Test @DisplayName("getWasteRecordById — not found throws")
    void getWasteRecordById_notFound() {
        when(wasteRecordRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> wasteService.getWasteRecordById(99L)).isInstanceOf(RuntimeException.class);
    }

    @Test @DisplayName("deleteWasteRecord — deletes")
    void deleteWasteRecord() {
        when(wasteRecordRepository.findById(1L)).thenReturn(Optional.of(wasteRecord));
        wasteService.deleteWasteRecord(1L);
        verify(wasteRecordRepository).delete(wasteRecord);
    }

    @Test @DisplayName("generateWasteReport — builds report")
    void generateWasteReport() {
        when(wasteRecordRepository.getTotalWasteCost(eq(1L), any(), any())).thenReturn(new BigDecimal("50000"));
        when(wasteRecordRepository.getTotalWasteQuantity(eq(1L), any(), any())).thenReturn(new BigDecimal("10"));
        when(wasteRecordRepository.getWasteRecordCount(eq(1L), any(), any())).thenReturn(3L);
        when(wasteRecordRepository.getMostCommonWasteReason(eq(1L), any(), any())).thenReturn(WasteRecord.WasteReason.EXPIRED);
        when(wasteRecordRepository.getWasteBreakdownByReason(eq(1L), any(), any())).thenReturn(List.of());
        when(wasteRecordRepository.getTopWastedIngredients(eq(1L), any(), any(), any())).thenReturn(List.of());
        when(wasteRecordRepository.getDailyWasteTotals(eq(1L), any(), any())).thenReturn(List.of());

        WasteReportResponse report = wasteService.generateWasteReport(1L, LocalDate.now().minusDays(30), LocalDate.now());
        assertThat(report.getTotalWasteCost()).isEqualByComparingTo("50000");
        assertThat(report.getRecordCount()).isEqualTo(3);
    }
}
