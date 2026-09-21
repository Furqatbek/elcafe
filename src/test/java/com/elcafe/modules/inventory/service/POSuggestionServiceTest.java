package com.elcafe.modules.inventory.service;

import com.elcafe.modules.financial.entity.PurchaseOrder;
import com.elcafe.modules.financial.entity.PurchaseOrderItem;
import com.elcafe.modules.financial.repository.PurchaseOrderItemRepository;
import com.elcafe.modules.financial.repository.PurchaseOrderRepository;
import com.elcafe.modules.financial.service.PurchaseOrderService;
import com.elcafe.modules.inventory.dto.GeneratePORequest;
import com.elcafe.modules.inventory.dto.POSuggestionResponse;
import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.Supplier;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.SupplierRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class POSuggestionServiceTest {

    @Mock private InventoryIngredientRepository ingredientRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Mock private PurchaseOrderService purchaseOrderService;
    @Mock private RestaurantRepository restaurantRepository;
    @InjectMocks private POSuggestionService poSuggestionService;

    private Restaurant restaurant;
    private Supplier supplier;
    private Ingredient flourLow;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test");

        supplier = Supplier.builder()
                .id(1L).name("Fresh Foods").code("FF001")
                .contactPerson("John").phone("+998901234567")
                .paymentTerms("Net 30").active(true).build();
        supplier.setRestaurant(restaurant);

        flourLow = Ingredient.builder()
                .id(1L).name("Flour").unit("kg")
                .currentStock(new BigDecimal("5"))
                .minimumStock(new BigDecimal("10"))
                .reorderLevel(new BigDecimal("20"))
                .reorderQuantity(new BigDecimal("50"))
                .costPerUnit(new BigDecimal("5000"))
                .trackInventory(true).active(true).version(0L).build();
        flourLow.setRestaurant(restaurant);
        flourLow.setSupplierEntity(supplier);
    }

    @Test @DisplayName("getSuggestions — groups by supplier with urgency")
    void groupsBySupplier() {
        when(ingredientRepository.findByRestaurantIdWithSupplier(1L)).thenReturn(List.of(flourLow));
        when(purchaseOrderRepository.findByRestaurant_IdAndStatusIn(eq(1L), any())).thenReturn(List.of());

        List<POSuggestionResponse> suggestions = poSuggestionService.getSuggestions(1L);

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).getSupplierName()).isEqualTo("Fresh Foods");
        assertThat(suggestions.get(0).getItemCount()).isEqualTo(1);
    }

    @Test @DisplayName("getSuggestions — excludes ingredients with pending POs")
    void excludesPendingPOs() {
        PurchaseOrder pendingPO = PurchaseOrder.builder()
                .id(1L).restaurant(restaurant).status(PurchaseOrder.Status.ORDERED).build();
        PurchaseOrderItem poItem = PurchaseOrderItem.builder()
                .id(1L).ingredient(flourLow).build();
        pendingPO.setItems(List.of(poItem));

        when(ingredientRepository.findByRestaurantIdWithSupplier(1L)).thenReturn(List.of(flourLow));
        when(purchaseOrderRepository.findByRestaurant_IdAndStatusIn(eq(1L), any())).thenReturn(List.of(pendingPO));

        List<POSuggestionResponse> suggestions = poSuggestionService.getSuggestions(1L);

        assertThat(suggestions).isEmpty();
    }

    @Test @DisplayName("getSuggestions — CRITICAL urgency when stock is zero")
    void criticalUrgency() {
        flourLow.setCurrentStock(BigDecimal.ZERO);
        when(ingredientRepository.findByRestaurantIdWithSupplier(1L)).thenReturn(List.of(flourLow));
        when(purchaseOrderRepository.findByRestaurant_IdAndStatusIn(eq(1L), any())).thenReturn(List.of());

        List<POSuggestionResponse> suggestions = poSuggestionService.getSuggestions(1L);

        assertThat(suggestions).hasSize(1);
        assertThat(suggestions.get(0).getUrgency()).isEqualTo(POSuggestionResponse.Urgency.CRITICAL);
    }

    @Test @DisplayName("getSuggestions — empty when no reorder needed")
    void noReorderNeeded() {
        Ingredient wellStocked = Ingredient.builder()
                .id(2L).name("Sugar").unit("kg")
                .currentStock(new BigDecimal("100"))
                .minimumStock(new BigDecimal("10"))
                .reorderLevel(new BigDecimal("20"))
                .trackInventory(true).active(true).version(0L).build();
        wellStocked.setRestaurant(restaurant);
        wellStocked.setSupplierEntity(supplier);

        when(ingredientRepository.findByRestaurantIdWithSupplier(1L)).thenReturn(List.of(wellStocked));
        when(purchaseOrderRepository.findByRestaurant_IdAndStatusIn(eq(1L), any())).thenReturn(List.of());

        List<POSuggestionResponse> suggestions = poSuggestionService.getSuggestions(1L);

        assertThat(suggestions).isEmpty();
    }

    @Test @DisplayName("generatePurchaseOrder — delegates to PurchaseOrderService")
    void generatePO() {
        GeneratePORequest request = GeneratePORequest.builder()
                .restaurantId(1L).supplierId(1L).build();
        PurchaseOrder createdPO = PurchaseOrder.builder()
                .id(10L).poNumber("PO-2026-001").restaurant(restaurant).build();

        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));
        when(ingredientRepository.findByRestaurantIdWithSupplier(1L)).thenReturn(List.of(flourLow));
        when(purchaseOrderService.createPurchaseOrder(any(PurchaseOrder.class), any())).thenReturn(createdPO);

        PurchaseOrder result = poSuggestionService.generatePurchaseOrder(request, "admin");

        assertThat(result.getId()).isEqualTo(10L);
    }

    @Test @DisplayName("getSuggestionCount — sums item counts")
    void suggestionCount() {
        when(ingredientRepository.findByRestaurantIdWithSupplier(1L)).thenReturn(List.of(flourLow));
        when(purchaseOrderRepository.findByRestaurant_IdAndStatusIn(eq(1L), any())).thenReturn(List.of());

        int count = poSuggestionService.getSuggestionCount(1L);

        assertThat(count).isEqualTo(1);
    }
}
