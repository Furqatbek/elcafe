package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.PurchaseOrder;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.repository.PurchaseOrderItemRepository;
import com.elcafe.modules.financial.repository.PurchaseOrderRepository;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.inventory.service.CostHistoryService;
import com.elcafe.modules.inventory.service.InventoryBatchService;
import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.inventory.service.InventoryValuationService;
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
import java.util.List;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createRestaurant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseOrderServiceTest {

    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private InventoryIngredientRepository ingredientRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private JournalService journalService;
    @Mock private InventoryService inventoryService;
    @Mock private ExpenseService expenseService;
    @Mock private InventoryBatchService batchService;
    @Mock private CostHistoryService costHistoryService;
    @Mock private InventoryValuationService valuationService;
    @InjectMocks private PurchaseOrderService purchaseOrderService;

    private PurchaseOrder po;

    @BeforeEach
    void setUp() {
        po = PurchaseOrder.builder().id(1L).restaurant(createRestaurant())
                .totalAmount(BigDecimal.valueOf(500000)).status(PurchaseOrder.Status.DRAFT).build();
    }

    @Test @DisplayName("getById found") void getById() {
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        assertEquals(1L, purchaseOrderService.getPurchaseOrderById(1L).getId());
    }
    @Test @DisplayName("getById not found") void getById_notFound() {
        when(purchaseOrderRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(Exception.class, () -> purchaseOrderService.getPurchaseOrderById(99L));
    }
    @Test @DisplayName("approve sets APPROVED") void approve() {
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        assertEquals(PurchaseOrder.Status.APPROVED, purchaseOrderService.approvePurchaseOrder(1L, "admin").getStatus());
    }
    @Test @DisplayName("getByRestaurant returns") void getByRestaurant() {
        when(purchaseOrderRepository.findByRestaurant_Id(1L)).thenReturn(List.of(po));
        assertEquals(1, purchaseOrderService.getPurchaseOrdersByRestaurant(1L).size());
    }
    @Test @DisplayName("getUnpaid returns") void getUnpaid() {
        when(purchaseOrderRepository.findUnpaidOrders(1L)).thenReturn(List.of(po));
        assertEquals(1, purchaseOrderService.getUnpaidOrders(1L).size());
    }
}
