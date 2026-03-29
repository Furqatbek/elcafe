package com.elcafe.modules.financial.controller;

import com.elcafe.modules.financial.entity.PurchaseOrder;
import com.elcafe.modules.financial.service.PurchaseOrderService;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.repository.SupplierRepository;
import com.elcafe.modules.financial.repository.ExpenseRepository;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderControllerTest {

    private MockMvc mockMvc;
    @Mock private PurchaseOrderService purchaseOrderService;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private InventoryIngredientRepository ingredientRepository;
    @Mock private SupplierRepository supplierRepository;
    @Mock private ExpenseRepository expenseRepository;
    @InjectMocks private PurchaseOrderController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test @DisplayName("GET / returns POs") void getAll() throws Exception {
        when(purchaseOrderService.getPurchaseOrdersByRestaurant(1L)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/financial/purchase-orders").param("restaurantId", "1")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /{id}") void getById() throws Exception {
        when(purchaseOrderService.getPurchaseOrderById(1L)).thenReturn(new PurchaseOrder());
        mockMvc.perform(get("/api/v1/financial/purchase-orders/1")).andExpect(status().isOk());
    }
}
