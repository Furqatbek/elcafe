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

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the durable fire-once gate of {@link OrderCompletionEvents} (audit FUNC-15): publish exactly
 * when an order qualifies (settled + fully paid + customer) AND has never fired before — the
 * {@code completionEventPublishedAt} marker, stamped in the same transaction as the publish, is the
 * only replay authority, so de-qualify/re-qualify cycles (tip after full payment, refund +
 * re-collection) can never fire twice, from any call site, in any order.
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
        lenient().when(order.getCompletionEventPublishedAt()).thenReturn(null);
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
        gate.publishIfQualified(order);
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("first qualifying call publishes, stamps the marker, and saves it in the same tx")
    void qualifyingCallPublishesAndStampsMarker() {
        enabledGate.publishIfQualified(order);
        verify(order).setCompletionEventPublishedAt(any(OffsetDateTime.class));
        verify(orderRepository).save(order);
        verify(publisher).publishOrderCompleted(order, customer, true);
    }

    @Test
    @DisplayName("marker already set → no publish, ever (the tip/refund re-qualification defense)")
    void markerSuppressesReplayForever() {
        when(order.getCompletionEventPublishedAt()).thenReturn(OffsetDateTime.now());
        enabledGate.publishIfQualified(order);
        verifyNoInteractions(publisher);
        verify(order, never()).setCompletionEventPublishedAt(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("second settled order for the customer publishes with isFirstOrder=false")
    void repeatCustomerNotFirstOrder() {
        when(orderRepository.countByCustomer_IdAndStatusIn(anyLong(), anyCollection())).thenReturn(2L);
        enabledGate.publishIfQualified(order);
        verify(publisher).publishOrderCompleted(order, customer, false);
    }

    @Test
    @DisplayName("no customer → no event, no marker (walk-in POS orders)")
    void noCustomerNoEvent() {
        when(order.getCustomer()).thenReturn(null);
        enabledGate.publishIfQualified(order);
        verifyNoInteractions(publisher);
        verify(order, never()).setCompletionEventPublishedAt(any());
    }

    @Test
    @DisplayName("settled but unpaid → no event yet; the marker stays clear so full payment can fire it")
    void unpaidNoEventYet() {
        when(order.isFullyPaid()).thenReturn(false);
        enabledGate.publishIfQualified(order);
        verifyNoInteractions(publisher);
        verify(order, never()).setCompletionEventPublishedAt(any());
    }

    @Test
    @DisplayName("paid but not settled → no event yet (fires later, from the settling site)")
    void notSettledNoEventYet() {
        when(order.getStatus()).thenReturn(OrderStatus.PREPARING);
        enabledGate.publishIfQualified(order);
        verifyNoInteractions(publisher);
        verify(order, never()).setCompletionEventPublishedAt(any());
    }

    @Test
    @DisplayName("DELIVERED counts as settled (courier/payment flows)")
    void deliveredIsSettled() {
        when(order.getStatus()).thenReturn(OrderStatus.DELIVERED);
        enabledGate.publishIfQualified(order);
        verify(publisher).publishOrderCompleted(order, customer, true);
    }

    @Test
    @DisplayName("marker-save failure means no publish — the next qualifying mutation retries")
    void saveFailureMeansNoPublish() {
        doThrow(new RuntimeException("optimistic lock")).when(orderRepository).save(order);
        enabledGate.publishIfQualified(order);
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("publisher failure is swallowed — the order flow must never break")
    void publisherFailureSwallowed() {
        doThrow(new RuntimeException("boom")).when(publisher)
                .publishOrderCompleted(any(), any(), any(Boolean.class));
        enabledGate.publishIfQualified(order);
        // no exception = pass
    }
}
