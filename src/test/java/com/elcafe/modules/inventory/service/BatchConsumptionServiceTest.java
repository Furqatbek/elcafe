package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.entity.BatchConsumption;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryBatch;
import com.elcafe.modules.inventory.enums.ValuationMethod;
import com.elcafe.modules.inventory.repository.BatchConsumptionRepository;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BatchConsumptionServiceTest {

    @Mock private BatchConsumptionRepository consumptionRepository;
    @Mock private InventoryIngredientRepository ingredientRepository;
    @InjectMocks private BatchConsumptionService batchConsumptionService;

    private Ingredient ingredient;
    private InventoryBatch batch;
    private BatchConsumption consumption;

    @BeforeEach
    void setUp() {
        ingredient = Ingredient.builder().id(1L).name("Flour").unit("kg")
                .currentStock(new BigDecimal("100")).costPerUnit(new BigDecimal("5000")).build();
        batch = InventoryBatch.builder().id(1L).ingredient(ingredient).batchNumber("B-001")
                .costPerUnit(new BigDecimal("5000")).quantity(new BigDecimal("50")).build();
        consumption = BatchConsumption.builder().id(1L).ingredient(ingredient).batch(batch)
                .quantity(new BigDecimal("10")).costPerUnit(new BigDecimal("5000"))
                .totalCost(new BigDecimal("50000")).valuationMethod(ValuationMethod.FIFO)
                .orderId(1L).consumedAt(LocalDateTime.now()).build();
    }

    @Test @DisplayName("recordConsumption — saves with cost calculation")
    void recordConsumption() {
        when(consumptionRepository.save(any())).thenAnswer(i -> { BatchConsumption c = i.getArgument(0); c.setId(1L); return c; });
        BatchConsumption result = batchConsumptionService.recordConsumption(batch, new BigDecimal("10"), ValuationMethod.FIFO, 1L, 1L);
        assertThat(result.getTotalCost()).isEqualByComparingTo("50000");
    }

    @Test @DisplayName("getConsumptionsForOrder — returns list")
    void getConsumptionsForOrder() {
        when(consumptionRepository.findByOrderId(1L)).thenReturn(List.of(consumption));
        assertThat(batchConsumptionService.getConsumptionsForOrder(1L)).hasSize(1);
    }

    @Test @DisplayName("calculateOrderCOGS — delegates to repo")
    void calculateOrderCOGS() {
        when(consumptionRepository.calculateOrderCOGS(1L)).thenReturn(new BigDecimal("50000"));
        assertThat(batchConsumptionService.calculateOrderCOGS(1L)).isEqualByComparingTo("50000");
    }

    @Test @DisplayName("calculateTotalCOGS — delegates to repo")
    void calculateTotalCOGS() {
        when(consumptionRepository.calculateTotalCOGS(eq(1L), any(), any())).thenReturn(new BigDecimal("500000"));
        assertThat(batchConsumptionService.calculateTotalCOGS(1L, LocalDateTime.now().minusDays(30), LocalDateTime.now()))
                .isEqualByComparingTo("500000");
    }

    @Test @DisplayName("getConsumptionHistory — returns ordered list")
    void getConsumptionHistory() {
        when(consumptionRepository.findByIngredientIdOrderByConsumedAtDesc(1L)).thenReturn(List.of(consumption));
        assertThat(batchConsumptionService.getConsumptionHistory(1L)).hasSize(1);
    }

    @Test @DisplayName("getConsumptionHistoryPaginated — returns page")
    void getConsumptionHistoryPaginated() {
        when(consumptionRepository.findByIngredientId(eq(1L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(consumption)));
        assertThat(batchConsumptionService.getConsumptionHistoryPaginated(1L, 0, 10).getTotalElements()).isEqualTo(1);
    }

    @Test @DisplayName("getConsumptionStats — calculates stats")
    void getConsumptionStats() {
        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
        when(consumptionRepository.findByIngredientIdAndDateRange(eq(1L), any(), any())).thenReturn(List.of(consumption));
        var stats = batchConsumptionService.getConsumptionStats(1L, LocalDateTime.now().minusDays(30), LocalDateTime.now());
        assertThat(stats.totalQuantity()).isEqualByComparingTo("10");
        assertThat(stats.transactionCount()).isEqualTo(1);
    }

    @Test @DisplayName("deleteConsumptionsForOrder — deletes when exists")
    void deleteConsumptionsForOrder() {
        when(consumptionRepository.countByOrderId(1L)).thenReturn(2L);
        batchConsumptionService.deleteConsumptionsForOrder(1L);
        verify(consumptionRepository).deleteByOrderId(1L);
    }
}
