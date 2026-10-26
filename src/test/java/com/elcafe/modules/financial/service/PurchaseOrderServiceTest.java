package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.PurchaseOrder;
import com.elcafe.modules.financial.entity.PurchaseOrderItem;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createRestaurant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Test @DisplayName("createAndFinalize books no inventory expense — COGS charges it at sale")
    void createAndFinalize_doesNotBookInventoryExpense() {
        PurchaseOrder draft = PurchaseOrder.builder().restaurant(createRestaurant())
                .subtotal(BigDecimal.valueOf(500000)).totalAmount(BigDecimal.valueOf(500000))
                .status(PurchaseOrder.Status.DRAFT).build();
        PurchaseOrderItem item = PurchaseOrderItem.builder().id(7L).itemName("Coffee beans")
                .quantity(BigDecimal.valueOf(10)).unitPrice(BigDecimal.valueOf(50000))
                .totalPrice(BigDecimal.valueOf(500000)).receivedQuantity(BigDecimal.ZERO).build();

        when(purchaseOrderRepository.findByRestaurant_Id(anyLong())).thenReturn(List.of());
        when(purchaseOrderRepository.save(any())).thenAnswer(i -> {
            PurchaseOrder saved = i.getArgument(0);
            if (saved.getId() == null) saved.setId(42L);
            return saved;
        });
        when(purchaseOrderRepository.findById(42L)).thenReturn(Optional.of(draft));
        when(purchaseOrderItemRepository.findById(7L)).thenReturn(Optional.of(item));

        purchaseOrderService.createAndFinalize(
                draft, List.of(item), "CASH", LocalDate.of(2026, 3, 1), "admin");

        assertEquals(PurchaseOrder.PaymentStatus.PAID, draft.getPaymentStatus());
        verify(expenseService, never()).createExpenseFromPurchaseOrder(
                any(), any(), any(), any(), any(), any(), any(), any());
    }
}
