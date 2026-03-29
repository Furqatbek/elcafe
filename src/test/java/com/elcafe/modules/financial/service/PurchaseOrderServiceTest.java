package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.PurchaseOrder;
import com.elcafe.modules.financial.entity.PurchaseOrderItem;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.repository.PurchaseOrderItemRepository;
import com.elcafe.modules.financial.repository.PurchaseOrderRepository;
import com.elcafe.modules.inventory.service.CostHistoryService;
import com.elcafe.modules.inventory.service.InventoryBatchService;
import com.elcafe.modules.inventory.service.InventoryService;
import com.elcafe.modules.inventory.service.InventoryValuationService;
import com.elcafe.modules.inventory.repository.InventoryIngredientRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createRestaurant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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
        po = new PurchaseOrder();
        po.setId(1L);
        po.setRestaurant(createRestaurant());
        po.setTotalAmount(BigDecimal.valueOf(500000));
        po.setStatus(PurchaseOrder.Status.DRAFT);
    }

    @Test
    @DisplayName("createPurchaseOrder — saves PO and items")
    void create_savesPoAndItems() {
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenReturn(po);
        PurchaseOrder result = purchaseOrderService.createPurchaseOrder(po, List.of());
        assertNotNull(result);
        verify(purchaseOrderRepository).save(po);
    }

    @Test
    @DisplayName("getPurchaseOrderById — found")
    void getById_found() {
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        assertEquals(1L, purchaseOrderService.getPurchaseOrderById(1L).getId());
    }

    @Test
    @DisplayName("getPurchaseOrderById — not found throws")
    void getById_notFound_throws() {
        when(purchaseOrderRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(Exception.class, () -> purchaseOrderService.getPurchaseOrderById(99L));
    }

    @Test
    @DisplayName("approvePurchaseOrder — sets APPROVED status")
    void approve_setsApproved() {
        when(purchaseOrderRepository.findById(1L)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(i -> i.getArgument(0));

        PurchaseOrder result = purchaseOrderService.approvePurchaseOrder(1L, "admin");
        assertEquals(PurchaseOrder.Status.APPROVED, result.getStatus());
    }

    @Test
    @DisplayName("getPurchaseOrdersByRestaurant — returns list")
    void getByRestaurant_returnsList() {
        when(purchaseOrderRepository.findByRestaurant_Id(1L)).thenReturn(List.of(po));
        assertEquals(1, purchaseOrderService.getPurchaseOrdersByRestaurant(1L).size());
    }

    @Test
    @DisplayName("getUnpaidOrders — returns unpaid")
    void getUnpaid_returns() {
        when(purchaseOrderRepository.findUnpaidOrders(anyLong())).thenReturn(List.of(po));
        assertEquals(1, purchaseOrderService.getUnpaidOrders(1L).size());
    }
}
