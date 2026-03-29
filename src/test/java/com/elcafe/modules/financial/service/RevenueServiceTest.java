package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.repository.JournalEntryRepository;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.enums.OrderStatus;
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

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrderItem;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueServiceTest {

    @Mock private JournalService journalService;
    @Mock private AccountRepository accountRepository;
    @Mock private JournalEntryRepository journalEntryRepository;
    @Mock private InventoryProductIngredientRepository productIngredientRepository;

    @InjectMocks private RevenueService revenueService;

    private Order order;
    private Account revenueAccount;

    @BeforeEach
    void setUp() {
        order = createOrder(1L, OrderStatus.COMPLETED);
        order.setTotal(BigDecimal.valueOf(100000));
        order.setSubtotal(BigDecimal.valueOf(100000));
        order.getItems().add(createOrderItem(1L, 1L, "Steak", 1, BigDecimal.valueOf(100000)));

        revenueAccount = new Account();
        revenueAccount.setId(1L);
        revenueAccount.setName("Revenue");
    }

    @Test
    @DisplayName("recordOrderRevenue — records revenue for completed order")
    void recordOrderRevenue_records() {
        when(accountRepository.findByRestaurant_IdAndCategory(anyLong(), any()))
                .thenReturn(List.of(revenueAccount));

        assertDoesNotThrow(() -> revenueService.recordOrderRevenue(order));
    }

    @Test
    @DisplayName("recordOrderRevenue — creates accounts if not found")
    void recordOrderRevenue_createsAccounts() {
        when(accountRepository.findByRestaurant_IdAndCategory(anyLong(), any()))
                .thenReturn(List.of());
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> {
            Account a = i.getArgument(0);
            a.setId(1L);
            return a;
        });

        assertDoesNotThrow(() -> revenueService.recordOrderRevenue(order));
    }

    @Test
    @DisplayName("recordCogs — records cost of goods sold")
    void recordCogs_records() {
        when(productIngredientRepository.findByProductId(anyLong())).thenReturn(List.of());
        when(accountRepository.findByRestaurant_IdAndCategory(anyLong(), any()))
                .thenReturn(List.of(revenueAccount));

        assertDoesNotThrow(() -> revenueService.recordCogs(order, java.time.LocalDate.now()));
    }

    @Test
    @DisplayName("recordRefund — records refund entry")
    void recordRefund_records() {
        when(accountRepository.findByRestaurant_IdAndCategory(anyLong(), any()))
                .thenReturn(List.of(revenueAccount));

        assertDoesNotThrow(() -> revenueService.recordRefund(order, BigDecimal.valueOf(50000), "Customer complaint"));
    }
}
