package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.dto.ValuationReportDTO.*;
import com.elcafe.modules.inventory.entity.BatchConsumption;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryBatch;
import com.elcafe.modules.inventory.enums.ValuationMethod;
import com.elcafe.modules.inventory.repository.BatchConsumptionRepository;
import com.elcafe.modules.inventory.repository.InventoryBatchRepository;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ValuationReportServiceTest {

    @Mock private InventoryValuationService valuationService;
    @Mock private InventoryIngredientRepository ingredientRepository;
    @Mock private InventoryBatchRepository batchRepository;
    @Mock private BatchConsumptionRepository consumptionRepository;
    @InjectMocks private ValuationReportService reportService;

    private Ingredient flour;

    @BeforeEach
    void setUp() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(1L);

        flour = Ingredient.builder()
                .id(1L).name("Flour").unit("kg")
                .currentStock(new BigDecimal("100"))
                .costPerUnit(new BigDecimal("5000"))
                .active(true).version(0L).build();
        flour.setRestaurant(restaurant);
    }

    @Test @DisplayName("generateComparisonReport — compares FIFO/LIFO/WAC per ingredient")
    void comparisonReport() {
        when(ingredientRepository.findByRestaurant_Id(1L)).thenReturn(List.of(flour));
        when(valuationService.getValuationMethod(1L)).thenReturn(ValuationMethod.WEIGHTED_AVERAGE);
        when(valuationService.calculateIngredientValue(1L, ValuationMethod.FIFO)).thenReturn(new BigDecimal("480000"));
        when(valuationService.calculateIngredientValue(1L, ValuationMethod.LIFO)).thenReturn(new BigDecimal("520000"));
        when(valuationService.calculateIngredientValue(1L, ValuationMethod.WEIGHTED_AVERAGE)).thenReturn(new BigDecimal("500000"));

        ValuationComparisonReport report = reportService.generateComparisonReport(1L);

        assertThat(report.getFifoValue()).isEqualByComparingTo("480000");
        assertThat(report.getLifoValue()).isEqualByComparingTo("520000");
        assertThat(report.getWeightedAverageValue()).isEqualByComparingTo("500000");
        assertThat(report.getIngredientComparisons()).hasSize(1);
        assertThat(report.getRecommendation()).isNotEmpty();
    }

    @Test @DisplayName("generateComparisonReport — empty when no ingredients with stock")
    void comparisonReportEmpty() {
        Ingredient emptyIngredient = Ingredient.builder()
                .id(2L).name("Salt").unit("kg")
                .currentStock(BigDecimal.ZERO).active(true).version(0L).build();
        when(ingredientRepository.findByRestaurant_Id(1L)).thenReturn(List.of(emptyIngredient));
        when(valuationService.getValuationMethod(1L)).thenReturn(ValuationMethod.WEIGHTED_AVERAGE);

        ValuationComparisonReport report = reportService.generateComparisonReport(1L);

        assertThat(report.getIngredientComparisons()).isEmpty();
        assertThat(report.getFifoValue()).isEqualByComparingTo("0");
    }

    @Test @DisplayName("generateValuationReport — full report with categories")
    void valuationReport() {
        when(ingredientRepository.findByRestaurant_Id(1L)).thenReturn(List.of(flour));
        when(valuationService.getValuationMethod(1L)).thenReturn(ValuationMethod.WEIGHTED_AVERAGE);
        when(valuationService.calculateIngredientValue(1L, ValuationMethod.WEIGHTED_AVERAGE))
                .thenReturn(new BigDecimal("500000"));
        when(batchRepository.findActiveBatchesFEFO(1L)).thenReturn(List.of());

        InventoryValuationReport report = reportService.generateValuationReport(1L, null);

        assertThat(report.getTotalInventoryValue()).isEqualByComparingTo("500000");
        assertThat(report.getTotalIngredients()).isEqualTo(1);
        assertThat(report.getIngredientsWithStock()).isEqualTo(1);
    }

    @Test @DisplayName("generateCostVarianceReport — actual vs standard analysis")
    void costVarianceReport() {
        BatchConsumption consumption = BatchConsumption.builder()
                .id(1L).ingredient(flour)
                .quantity(new BigDecimal("10"))
                .totalCost(new BigDecimal("55000"))
                .costPerUnit(new BigDecimal("5500"))
                .build();
        when(consumptionRepository.findByRestaurantAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(consumption));
        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(flour));

        CostVarianceReport report = reportService.generateCostVarianceReport(
                1L, LocalDateTime.now().minusDays(30), LocalDateTime.now());

        assertThat(report.getTotalActualCost()).isEqualByComparingTo("55000");
        assertThat(report.getTotalStandardCost()).isEqualByComparingTo("50000");
        assertThat(report.getIngredientVariances()).hasSize(1);
    }
}
