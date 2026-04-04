package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryBatch;
import com.elcafe.modules.inventory.entity.ValuationSettings;
import com.elcafe.modules.inventory.enums.ValuationMethod;
import com.elcafe.modules.inventory.repository.BatchConsumptionRepository;
import com.elcafe.modules.inventory.repository.InventoryBatchRepository;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.ValuationSettingsRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryValuationServiceTest {

    @Mock private InventoryBatchRepository batchRepository;
    @Mock private InventoryIngredientRepository ingredientRepository;
    @Mock private ValuationSettingsRepository valuationSettingsRepository;
    @Mock private BatchConsumptionRepository consumptionRepository;
    @InjectMocks private InventoryValuationService valuationService;

    private Restaurant restaurant;
    private Ingredient ingredient;
    private InventoryBatch batch1, batch2;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(valuationService, "strictExpiryEnforcement", true);
        restaurant = new Restaurant(); restaurant.setId(1L);
        ingredient = Ingredient.builder().id(1L).name("Flour").unit("kg")
                .currentStock(new BigDecimal("100")).costPerUnit(new BigDecimal("5000"))
                .weightedAverageCost(new BigDecimal("4800")).trackExpiry(true).build();
        ingredient.setRestaurant(restaurant);
        batch1 = InventoryBatch.builder().id(1L).ingredient(ingredient).batchNumber("B-OLD")
                .quantity(new BigDecimal("30")).costPerUnit(new BigDecimal("4500"))
                .receivedDate(LocalDate.now().minusDays(20)).expiryDate(LocalDate.now().plusDays(10))
                .status(InventoryBatch.Status.ACTIVE).build();
        batch2 = InventoryBatch.builder().id(2L).ingredient(ingredient).batchNumber("B-NEW")
                .quantity(new BigDecimal("70")).costPerUnit(new BigDecimal("5500"))
                .receivedDate(LocalDate.now().minusDays(5)).expiryDate(LocalDate.now().plusDays(25))
                .status(InventoryBatch.Status.ACTIVE).build();
    }

    @Test @DisplayName("getValuationMethod — returns configured or default WAC")
    void getValuationMethod() {
        ValuationSettings settings = ValuationSettings.builder().valuationMethod(ValuationMethod.FIFO).isActive(true).build();
        when(valuationSettingsRepository.findByRestaurant_IdAndIsActiveTrue(1L)).thenReturn(Optional.of(settings));
        assertThat(valuationService.getValuationMethod(1L)).isEqualTo(ValuationMethod.FIFO);
    }

    @Test @DisplayName("getValuationMethod — defaults to WAC when no settings")
    void getValuationMethod_default() {
        when(valuationSettingsRepository.findByRestaurant_IdAndIsActiveTrue(1L)).thenReturn(Optional.empty());
        assertThat(valuationService.getValuationMethod(1L)).isEqualTo(ValuationMethod.WEIGHTED_AVERAGE);
    }

    @Test @DisplayName("setValuationMethod — saves new settings")
    void setValuationMethod() {
        when(valuationSettingsRepository.findByRestaurant_IdAndIsActiveTrue(1L)).thenReturn(Optional.empty());
        when(valuationSettingsRepository.save(any())).thenAnswer(i -> { ValuationSettings s = i.getArgument(0); s.setId(1L); return s; });
        ValuationSettings result = valuationService.setValuationMethod(1L, ValuationMethod.LIFO, "admin");
        assertThat(result.getValuationMethod()).isEqualTo(ValuationMethod.LIFO);
    }

    @Test @DisplayName("calculateInventoryValue — sums ingredient values")
    void calculateInventoryValue() {
        when(ingredientRepository.findByRestaurant_IdAndActiveTrue(1L)).thenReturn(List.of(ingredient));
        when(batchRepository.findActiveBatchesFEFO(1L)).thenReturn(List.of(batch1, batch2));
        var result = valuationService.calculateInventoryValue(1L, ValuationMethod.WEIGHTED_AVERAGE);
        assertThat(result.totalValue()).isNotNull();
    }

    @Test @DisplayName("calculateIngredientValue — WAC uses effective cost")
    void calculateIngredientValue_WAC() {
        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
        when(batchRepository.findActiveBatchesFEFO(1L)).thenReturn(List.of(batch1, batch2));
        BigDecimal value = valuationService.calculateIngredientValue(1L, ValuationMethod.WEIGHTED_AVERAGE);
        assertThat(value).isNotNull();
    }

    @Test @DisplayName("recalculateWAC — recalculates from active batches")
    void recalculateWAC() {
        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
        when(batchRepository.findActiveBatchesFEFO(1L)).thenReturn(List.of(batch1, batch2));
        when(ingredientRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        BigDecimal wac = valuationService.recalculateWAC(1L);
        assertThat(wac).isNotNull();
    }

    @Test @DisplayName("consumeWithValuation — delegates to configured method")
    void consumeWithValuation() {
        when(valuationSettingsRepository.findByRestaurant_IdAndIsActiveTrue(1L)).thenReturn(Optional.empty()); // defaults to WAC
        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
        when(batchRepository.findActiveBatchesFEFO(1L)).thenReturn(List.of(batch1, batch2));
        when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(ingredientRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(consumptionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = valuationService.consumeWithValuation(1L, new BigDecimal("10"), 100L);
        assertThat(result.quantityConsumed()).isEqualByComparingTo("10");
        assertThat(result.totalCost()).isNotNull();
    }
}
