package com.elcafe.modules.partner.service;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.order.service.OrderService;
import com.elcafe.modules.partner.dto.PartnerOrderRequest;
import com.elcafe.modules.partner.dto.PartnerOrderResponse;
import com.elcafe.modules.partner.entity.Partner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the orchestration only: which transaction runs, in what order, and what survives a failure.
 *
 * <p>Note what these tests can and cannot prove. They pin the <em>sequence</em> — create, then accept,
 * with the accept's failure isolated — but a Mockito mock has no transaction interceptor, so they
 * cannot show the rollback semantics that made the split necessary in the first place. That is what
 * {@code PartnerAutoAcceptFailureIntegrationTest} is for, and it is the test that actually fails if
 * anyone folds these two transactions back into one.
 */
@ExtendWith(MockitoExtension.class)
class PartnerOrderPusherTest {

    @Mock private PartnerOrderService partnerOrderService;
    @Mock private OrderService orderService;

    @InjectMocks private PartnerOrderPusher pusher;

    private Partner partner;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(pusher, "autoAccept", true);
        partner = Partner.builder().id(7L).name("Test Aggregator").slug("test-agg").active(true).build();
    }

    private PartnerOrderRequest request() {
        return PartnerOrderRequest.builder()
                .restaurantId(3L)
                .externalOrderId("EXT-1")
                .orderType(OrderType.TAKEAWAY)
                .paymentMode(PartnerOrderRequest.PaymentMode.PREPAID)
                .items(List.of(PartnerOrderRequest.Item.builder().productId(1L).quantity(1).build()))
                .build();
    }

    private PartnerOrderResponse created(OrderStatus status, boolean duplicate) {
        return PartnerOrderResponse.builder()
                .orderId(500L).orderNumber("ORD-500").externalOrderId("EXT-1")
                .status(status).total(new BigDecimal("30000")).duplicate(duplicate)
                .build();
    }

    @Test
    @DisplayName("a created order is accepted in a second step")
    void push_autoAccepts() {
        when(partnerOrderService.createOrderInTransaction(any(), any()))
                .thenReturn(created(OrderStatus.NEW, false));
        when(orderService.updateOrderStatus(any(), any(), any(), any()))
                .thenReturn(Order.builder().id(500L).status(OrderStatus.ACCEPTED).build());

        PartnerOrderResponse response = pusher.pushOrder(partner, request());

        assertThat(response.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
    }

    @Test
    @DisplayName("a refused accept leaves the order standing at NEW rather than failing the push")
    void push_acceptFailure_keepsOrder() {
        when(partnerOrderService.createOrderInTransaction(any(), any()))
                .thenReturn(created(OrderStatus.NEW, false));
        // Accepting deducts ingredients and can legitimately refuse.
        when(orderService.updateOrderStatus(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("Insufficient ingredients"));

        PartnerOrderResponse response = pusher.pushOrder(partner, request());

        // The partner has already charged their customer and the ticket has printed — the order must
        // survive for a human to accept.
        assertThat(response.getStatus()).isEqualTo(OrderStatus.NEW);
        assertThat(response.getOrderNumber()).isEqualTo("ORD-500");
    }

    @Test
    @DisplayName("auto-accept off leaves the order at NEW and never calls the status machine")
    void push_autoAcceptDisabled() {
        ReflectionTestUtils.setField(pusher, "autoAccept", false);
        when(partnerOrderService.createOrderInTransaction(any(), any()))
                .thenReturn(created(OrderStatus.NEW, false));

        PartnerOrderResponse response = pusher.pushOrder(partner, request());

        assertThat(response.getStatus()).isEqualTo(OrderStatus.NEW);
        verify(orderService, never()).updateOrderStatus(any(), any(), any(), any());
    }

    @Test
    @DisplayName("a replay is not re-accepted — it is already whatever it already is")
    void push_duplicate_isNotReAccepted() {
        when(partnerOrderService.createOrderInTransaction(any(), any()))
                .thenReturn(created(OrderStatus.PREPARING, true));

        PartnerOrderResponse response = pusher.pushOrder(partner, request());

        assertThat(response.getDuplicate()).isTrue();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PREPARING);
        verify(orderService, never()).updateOrderStatus(any(), any(), any(), any());
    }

    @Test
    @DisplayName("losing a concurrent duplicate race returns the winner, not an unbranded conflict")
    void push_uniqueViolation_returnsWinner() {
        when(partnerOrderService.createOrderInTransaction(any(), any()))
                .thenThrow(new DataIntegrityViolationException("uq_partner_order_external"));
        when(partnerOrderService.requireExistingOrder(any(), any()))
                .thenReturn(created(OrderStatus.ACCEPTED, true));

        PartnerOrderResponse response = pusher.pushOrder(partner, request());

        // "Did my order land?" — yes. A bare 409 would leave the partner guessing, and possibly
        // cancelling an order the kitchen is already cooking.
        assertThat(response.getDuplicate()).isTrue();
        assertThat(response.getOrderNumber()).isEqualTo("ORD-500");
    }
}
