package com.elcafe.modules.order.service;

import com.elcafe.common.audit.service.AuditService;
import com.elcafe.modules.financial.service.RevenueRecordingService;
import com.elcafe.modules.financial.service.RevenueService;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.dto.pos.PaymentRequestDTO;
import com.elcafe.modules.order.dto.pos.PaymentResponseDTO;
import com.elcafe.modules.order.exception.PaymentTransactionException;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrderItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private RevenueService revenueService;
    @Mock private RevenueRecordingService revenueRecordingService;
    @Mock private PaymentIdempotencyService idempotencyService;
    @Mock private AuditService auditService;
    @Mock private POSTableService posTableService;

    @InjectMocks private PaymentService paymentService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = createOrder(1L, OrderStatus.PREPARING);
        order.setTotal(BigDecimal.valueOf(100000));
        order.setGrandTotal(BigDecimal.valueOf(100000));
        order.setSubtotal(BigDecimal.valueOf(100000));
        order.setDiscount(BigDecimal.ZERO);
        order.setPayments(new ArrayList<>());

        // Add an item so order is valid
        order.getItems().add(createOrderItem(1L, 1L, "Steak", 1, BigDecimal.valueOf(100000)));
    }

    private PaymentRequestDTO buildPaymentRequest(PaymentMethod method, BigDecimal amount) {
        PaymentRequestDTO req = new PaymentRequestDTO();
        req.setMethod(method);
        req.setAmount(amount);
        return req;
    }

    private void stubIdempotency() {
        when(idempotencyService.acquireOrderPaymentLock(anyLong())).thenReturn(true);
        when(idempotencyService.getProcessedOrderForTransaction(any())).thenReturn(null);
    }

    // ==================== processPOSPayment ====================

    @Test
    @DisplayName("Cash payment — correct change calculated")
    void cashPayment_success() {
        stubIdempotency();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0); p.setId(1L); return p;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        PaymentRequestDTO req = buildPaymentRequest(PaymentMethod.CASH, BigDecimal.valueOf(100000));
        req.setAmountTendered(BigDecimal.valueOf(120000));

        PaymentResponseDTO result = paymentService.processPOSPayment(1L, req);

        assertNotNull(result);
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertEquals(0, BigDecimal.valueOf(20000).compareTo(captor.getValue().getChangeDue()));
    }

    @Test
    @DisplayName("Card payment — success")
    void cardPayment_success() {
        stubIdempotency();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0); p.setId(1L); return p;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        PaymentRequestDTO req = buildPaymentRequest(PaymentMethod.CARD, BigDecimal.valueOf(100000));

        PaymentResponseDTO result = paymentService.processPOSPayment(1L, req);

        assertNotNull(result);
    }

    @Test
    @DisplayName("Payment with tip — updates grandTotal")
    void paymentWithTip_updatesGrandTotal() {
        stubIdempotency();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0); p.setId(1L); return p;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        PaymentRequestDTO req = buildPaymentRequest(PaymentMethod.CASH, BigDecimal.valueOf(100000));
        req.setTipAmount(BigDecimal.valueOf(10000));
        req.setAmountTendered(BigDecimal.valueOf(110000));

        paymentService.processPOSPayment(1L, req);

        assertEquals(0, BigDecimal.valueOf(10000).compareTo(order.getTipAmount()));
        assertEquals(0, BigDecimal.valueOf(110000).compareTo(order.getGrandTotal()));
    }

    @Test
    @DisplayName("Full payment — sets order DELIVERED")
    void fullPayment_setsOrderDelivered() {
        stubIdempotency();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0); p.setId(1L); return p;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        PaymentRequestDTO req = buildPaymentRequest(PaymentMethod.CARD, BigDecimal.valueOf(100000));

        paymentService.processPOSPayment(1L, req);

        assertEquals(OrderStatus.DELIVERED, order.getStatus());
        assertEquals(PaymentStatus.COMPLETED, order.getPaymentStatus());
    }

    @Test
    @DisplayName("Partial payment — order NOT fully paid")
    void partialPayment_orderNotFullyPaid() {
        stubIdempotency();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0); p.setId(1L); return p;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        PaymentRequestDTO req = buildPaymentRequest(PaymentMethod.CARD, BigDecimal.valueOf(50000));

        paymentService.processPOSPayment(1L, req);

        // Order should NOT be DELIVERED since only 50% paid
        assertEquals(OrderStatus.PREPARING, order.getStatus());
    }

    @Test
    @DisplayName("Split payment — sets split number")
    void splitPayment_setsSplitNumber() {
        stubIdempotency();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
            Payment p = i.getArgument(0); p.setId(1L); return p;
        });
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        PaymentRequestDTO req = buildPaymentRequest(PaymentMethod.CASH, BigDecimal.valueOf(50000));
        req.setSplitNumber(1);
        req.setAmountTendered(BigDecimal.valueOf(50000));

        paymentService.processPOSPayment(1L, req);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getSplitNumber());
    }

    @Test
    @DisplayName("Order not found — throws")
    void payment_orderNotFound_throws() {
        stubIdempotency();
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        PaymentRequestDTO req = buildPaymentRequest(PaymentMethod.CASH, BigDecimal.valueOf(100000));

        assertThrows(PaymentTransactionException.class,
                () -> paymentService.processPOSPayment(99L, req));
    }

    @Test
    @DisplayName("Cancelled order — throws")
    void payment_cancelledOrder_throws() {
        order.setStatus(OrderStatus.CANCELLED);
        stubIdempotency();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        PaymentRequestDTO req = buildPaymentRequest(PaymentMethod.CASH, BigDecimal.valueOf(100000));

        assertThrows(PaymentTransactionException.class,
                () -> paymentService.processPOSPayment(1L, req));
    }

    @Test
    @DisplayName("Concurrent payment lock not acquired — throws")
    void payment_concurrentLock_throws() {
        when(idempotencyService.acquireOrderPaymentLock(1L)).thenReturn(false);

        PaymentRequestDTO req = buildPaymentRequest(PaymentMethod.CASH, BigDecimal.valueOf(100000));

        assertThrows(PaymentTransactionException.class,
                () -> paymentService.processPOSPayment(1L, req));
    }

    @Test
    @DisplayName("Duplicate transaction ID — returns existing")
    void payment_duplicateTransaction_returnsExisting() {
        when(idempotencyService.getProcessedOrderForTransaction("TXN-001")).thenReturn(1L);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        PaymentRequestDTO req = buildPaymentRequest(PaymentMethod.CASH, BigDecimal.valueOf(100000));
        req.setTransactionId("TXN-001");

        PaymentResponseDTO result = paymentService.processPOSPayment(1L, req);

        assertNotNull(result);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Cash tendered less than amount — throws")
    void payment_cashTenderedLessThanAmount_throws() {
        stubIdempotency();
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        PaymentRequestDTO req = buildPaymentRequest(PaymentMethod.CASH, BigDecimal.valueOf(100000));
        req.setAmountTendered(BigDecimal.valueOf(50000));

        assertThrows(PaymentTransactionException.class,
                () -> paymentService.processPOSPayment(1L, req));
    }

    // ==================== Lock release ====================

    @Test
    @DisplayName("Lock is released even on failure")
    void payment_lockReleasedOnFailure() {
        when(idempotencyService.acquireOrderPaymentLock(1L)).thenReturn(true);
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        PaymentRequestDTO req = buildPaymentRequest(PaymentMethod.CASH, BigDecimal.valueOf(100000));

        try {
            paymentService.processPOSPayment(1L, req);
        } catch (PaymentTransactionException ignored) {}

        verify(idempotencyService).releaseOrderPaymentLock(1L);
    }
}
