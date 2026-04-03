package com.elcafe.modules.financial.service;

import com.elcafe.modules.financial.entity.Account;
import com.elcafe.modules.financial.repository.AccountRepository;
import com.elcafe.modules.financial.repository.JournalEntryRepository;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
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

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrderItem;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RevenueServiceTest {

    @Mock private JournalService journalService;
    @Mock private AccountRepository accountRepository;
    @Mock private JournalEntryRepository journalEntryRepository;
    @Mock private InventoryProductIngredientRepository productIngredientRepository;
    @InjectMocks private RevenueService revenueService;

    @Test @DisplayName("recordOrderRevenue — records") void recordRevenue() {
        Order order = createOrder(1L, OrderStatus.COMPLETED);
        order.setTotal(BigDecimal.valueOf(100000));
        order.getItems().add(createOrderItem(1L, 1L, "Steak", 1, BigDecimal.valueOf(100000)));
        Account acc = new Account(); acc.setId(1L); acc.setBalance(BigDecimal.ZERO);
        when(accountRepository.findByRestaurant_IdAndCategory(anyLong(), any())).thenReturn(List.of(acc));
        assertDoesNotThrow(() -> revenueService.recordOrderRevenue(order));
    }
}
