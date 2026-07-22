package com.elcafe.modules.inventory.service;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.InventoryReservation;
import com.elcafe.modules.inventory.entity.ProductIngredient;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.inventory.repository.InventoryReservationRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InventoryReservationServiceTest {

    @Mock private InventoryReservationRepository reservationRepository;
    @Mock private InventoryIngredientRepository ingredientRepository;
    @Mock private InventoryProductIngredientRepository productIngredientRepository;
    @InjectMocks private InventoryReservationService reservationService;

    private Ingredient ingredient;

    @BeforeEach
    void setUp() {
        ingredient = Ingredient.builder().id(1L).name("Flour").unit("kg")
                .currentStock(new BigDecimal("100")).trackInventory(true).build();
    }

    @Test @DisplayName("reserveForProduct — creates reservations for recipe ingredients")
    void reserveForProduct_success() {
        ProductIngredient pi = ProductIngredient.builder().id(1L).ingredient(ingredient)
                .quantityRequired(new BigDecimal("0.5")).optional(false).build();
        when(productIngredientRepository.findByProductIdWithIngredients(1L)).thenReturn(List.of(pi));
        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
        when(reservationRepository.getTotalReservedQuantity(eq(1L), any())).thenReturn(BigDecimal.ZERO);
        when(reservationRepository.save(any())).thenAnswer(i -> { InventoryReservation r = i.getArgument(0); r.setId(1L); return r; });

        List<InventoryReservation> result = reservationService.reserveForProduct("session-1", 1L, 2);
        assertThat(result).hasSize(1);
    }

    @Test @DisplayName("reserveForProduct — insufficient stock throws")
    void reserveForProduct_insufficientStock() {
        ingredient.setCurrentStock(new BigDecimal("0.1"));
        ProductIngredient pi = ProductIngredient.builder().id(1L).ingredient(ingredient)
                .quantityRequired(new BigDecimal("5")).optional(false).build();
        when(productIngredientRepository.findByProductIdWithIngredients(1L)).thenReturn(List.of(pi));
        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
        when(reservationRepository.getTotalReservedQuantity(eq(1L), any())).thenReturn(BigDecimal.ZERO);

        assertThatThrownBy(() -> reservationService.reserveForProduct("session-1", 1L, 2))
                .isInstanceOf(InventoryReservationService.InsufficientStockException.class);
    }

    @Test @DisplayName("getAvailableStock — subtracts reserved from current")
    void getAvailableStock() {
        when(ingredientRepository.findById(1L)).thenReturn(Optional.of(ingredient));
        when(reservationRepository.getTotalReservedQuantity(eq(1L), any())).thenReturn(new BigDecimal("20"));
        assertThat(reservationService.getAvailableStock(1L)).isEqualByComparingTo("80");
    }

    @Test @DisplayName("releaseSessionReservations — releases all for session")
    void releaseSessionReservations() {
        when(reservationRepository.releaseSessionReservations(eq("session-1"), any())).thenReturn(2);
        assertThat(reservationService.releaseSessionReservations("session-1")).isEqualTo(2);
    }

    @Test @DisplayName("confirmReservations — confirms for order")
    void confirmReservations() {
        when(reservationRepository.confirmSessionReservations(eq("session-1"), eq(100L), any())).thenReturn(2);
        assertThat(reservationService.confirmReservations("session-1", 100L)).isEqualTo(2);
    }

    @Test @DisplayName("getSessionReservations — returns list")
    void getSessionReservations() {
        when(reservationRepository.findBySessionIdAndStatus("session-1", InventoryReservation.ReservationStatus.PENDING))
                .thenReturn(List.of());
        assertThat(reservationService.getSessionReservations("session-1")).isEmpty();
    }
}
