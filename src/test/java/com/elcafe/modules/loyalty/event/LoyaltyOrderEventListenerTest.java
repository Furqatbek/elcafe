package com.elcafe.modules.loyalty.event;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.loyalty.service.LoyaltyService;
import com.elcafe.modules.loyalty.service.MilestoneService;
import com.elcafe.modules.marketing.event.OrderCompletedEvent;
import com.elcafe.modules.order.entity.Order;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the hardening of the order-completed loyalty listener: a null customer is skipped instead of
 * NPE-ing the whole loyalty+milestone batch, and an already-processed order (bonus ledger idempotency
 * key present) is not replayed — the ledger dedupes, but loyalty stats and milestone visit counters
 * would double-count.
 */
@ExtendWith(MockitoExtension.class)
class LoyaltyOrderEventListenerTest {

    @Mock private LoyaltyService loyaltyService;
    @Mock private MilestoneService milestoneService;
    @Mock private Order order;
    @Mock private Customer customer;
    @InjectMocks private LoyaltyOrderEventListener listener;

    private OrderCompletedEvent event(Customer c) {
        lenient().when(order.getId()).thenReturn(5L);
        lenient().when(order.getTotal()).thenReturn(new BigDecimal("10"));
        return new OrderCompletedEvent(this, order, c, false);
    }

    @Test
    @DisplayName("normal event → loyalty and milestones both process")
    void normalEventProcesses() {
        when(loyaltyService.hasProcessedOrderCompletion(5L)).thenReturn(false);
        listener.handleOrderCompleted(event(customer));
        verify(loyaltyService).processOrderCompletion(order);
        verify(milestoneService).processOrderCompletion(order);
    }

    @Test
    @DisplayName("null customer → skipped entirely, no NPE")
    void nullCustomerSkipped() {
        listener.handleOrderCompleted(event(null));
        verify(loyaltyService, never()).processOrderCompletion(order);
        verify(milestoneService, never()).processOrderCompletion(order);
    }

    @Test
    @DisplayName("replayed event for an already-processed order → skipped")
    void replaySkipped() {
        when(loyaltyService.hasProcessedOrderCompletion(5L)).thenReturn(true);
        listener.handleOrderCompleted(event(customer));
        verify(loyaltyService, never()).processOrderCompletion(order);
        verify(milestoneService, never()).processOrderCompletion(order);
    }
}
