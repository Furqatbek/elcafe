package com.elcafe.modules.marketing.event;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the fire-once qualification of {@link OrderCompletionEvents} (audit FUNC-15): publish exactly
 * when an order FIRST satisfies settled+paid+has-customer, never on replays of an already-qualified
 * state, never when disabled, and never let a failure escape into the order flow.
 */
@ExtendWith(MockitoExtension.class)
class OrderCompletionEventsTest {

    @Mock private MarketingEventPublisher publisher;
    @Mock private OrderRepository orderRepository;
    @Mock private Order order;
    @Mock private Customer customer;

    private OrderCompletionEvents enabledGate;

    @BeforeEach
    void setUp() {
        enabledGate = new OrderCompletionEvents(publisher, orderRepository, true);
        lenient().when(customer.getId()).thenReturn(9L);
        lenient().when(order.getCustomer()).thenReturn(customer);
        lenient().when(order.getStatus()).thenReturn(OrderStatus.COMPLETED);
        lenient().when(order.isFullyPaid()).thenReturn(true);
        lenient().when(orderRepository.countByCustomer_IdAndStatusIn(anyLong(), anyCollection()))
                .thenReturn(1L);
    }

    @Test
    @DisplayName("disabled gate never publishes, even for a fully qualified order")
    void disabledNeverPublishes() {
        OrderCompletionEvents gate = new OrderCompletionEvents(publisher, orderRepository, false);
        gate.publishIfQualified(order, OrderStatus.PICKED_UP, true);
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("first qualifying edge publishes with isFirstOrder from the settled-order count")
    void qualifyingEdgePublishes() {
        enabledGate.publishIfQualified(order, OrderStatus.PICKED_UP, true);
        verify(publisher).publishOrderCompleted(order, customer, true);
    }

    @Test
    @DisplayName("second settled order for the customer publishes with isFirstOrder=false")
    void repeatCustomerNotFirstOrder() {
        when(orderRepository.countByCustomer_IdAndStatusIn(anyLong(), anyCollection())).thenReturn(2L);
        enabledGate.publishIfQualified(order, OrderStatus.PICKED_UP, true);
        verify(publisher).publishOrderCompleted(order, customer, false);
    }

    @Test
    @DisplayName("no customer → no event (walk-in POS orders; listeners dereference the customer)")
    void noCustomerNoEvent() {
        when(order.getCustomer()).thenReturn(null);
        enabledGate.publishIfQualified(order, OrderStatus.PICKED_UP, true);
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("settled but unpaid → no event (loyalty accrues on money actually received)")
    void unpaidNoEvent() {
        when(order.isFullyPaid()).thenReturn(false);
        enabledGate.publishIfQualified(order, OrderStatus.PICKED_UP, false);
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("paid but not settled → no event yet (fires later, from the settling site)")
    void notSettledNoEvent() {
        when(order.getStatus()).thenReturn(OrderStatus.PREPARING);
        enabledGate.publishIfQualified(order, OrderStatus.ACCEPTED, true);
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("already qualified before the mutation (DELIVERED+paid → COMPLETED) → no second event")
    void alreadyQualifiedNoReplay() {
        enabledGate.publishIfQualified(order, OrderStatus.DELIVERED, true);
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("newly created order (null previousStatus) counts as a qualifying edge")
    void newlyCreatedQualifies() {
        enabledGate.publishIfQualified(order, null, false);
        verify(publisher).publishOrderCompleted(eq(order), eq(customer), any(Boolean.class));
    }

    @Test
    @DisplayName("paid-side edge: was settled but unpaid, payment completes → publishes")
    void paymentCompletionEdgePublishes() {
        when(order.getStatus()).thenReturn(OrderStatus.DELIVERED);
        enabledGate.publishIfQualified(order, OrderStatus.DELIVERED, false);
        verify(publisher).publishOrderCompleted(order, customer, true);
    }

    @Test
    @DisplayName("publisher failure is swallowed — the order flow must never break")
    void publisherFailureSwallowed() {
        doThrow(new RuntimeException("boom")).when(publisher)
                .publishOrderCompleted(any(), any(), any(Boolean.class));
        enabledGate.publishIfQualified(order, OrderStatus.PICKED_UP, true);
        // no exception = pass
    }
}
