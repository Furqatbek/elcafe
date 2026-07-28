package com.elcafe.modules.order.service;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.exception.BadRequestException;

import com.elcafe.common.audit.entity.AuditAction;
import com.elcafe.common.audit.service.AuditService;
import com.elcafe.modules.financial.service.RevenueRecordingService;
import com.elcafe.modules.financial.service.RevenueService;
import com.elcafe.modules.kitchen.service.KitchenOrderService;
import com.elcafe.modules.order.dto.pos.PaymentRequestDTO;
import com.elcafe.modules.order.dto.pos.PaymentResponseDTO;
import com.elcafe.modules.order.dto.pos.RefundRequestDTO;
import com.elcafe.modules.marketing.event.OrderCompletionEvents;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.order.enums.OrderSource;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.enums.OrderType;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.exception.PaymentTransactionException;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.repository.PaymentRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private RevenueService revenueService;

    @Mock
    private RevenueRecordingService revenueRecordingService;

    @Mock
    private PaymentIdempotencyService idempotencyService;

    @Mock
    private AuditService auditService;

    @Mock
    private POSTableService posTableService;

    @Mock
    private OrderCompletionEvents orderCompletionEvents;

    @Mock
    private KitchenOrderService kitchenOrderService;

    @InjectMocks
    private PaymentService paymentService;

    @Captor
    private ArgumentCaptor<Payment> paymentCaptor;

    @Captor
    private ArgumentCaptor<Order> orderCaptor;

    private Order testOrder;
    private Restaurant testRestaurant;

    @BeforeEach
    void setUp() {
        testRestaurant = new Restaurant();
        testRestaurant.setId(1L);
        testRestaurant.setName("Test Restaurant");

        testOrder = Order.builder()
                .orderNumber("W-TEST-001")
                .restaurant(testRestaurant)
                .status(OrderStatus.PREPARING)
                .orderType(OrderType.DINE_IN)
                .orderSource(OrderSource.WAITER)
                .subtotal(new BigDecimal("100000"))
                .deliveryFee(BigDecimal.ZERO)
                .tax(new BigDecimal("10000"))
                .discount(BigDecimal.ZERO)
                .serviceFeePercent(BigDecimal.ZERO)
                .serviceFee(BigDecimal.ZERO)
                .entryFee(BigDecimal.ZERO)
                .total(new BigDecimal("100000"))
                .grandTotal(new BigDecimal("100000"))
                .tipAmount(BigDecimal.ZERO)
                .bonusUsed(BigDecimal.ZERO)
                .items(new ArrayList<>())
                .payments(new ArrayList<>())
                .build();
        testOrder.setId(1L);
        testOrder.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        testOrder.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        // Add an item so the order is realistic
        OrderItem item = OrderItem.builder()
                .id(1L)
                .productId(1L)
                .productName("Steak")
                .quantity(1)
                .unitPrice(new BigDecimal("100000"))
                .totalPrice(new BigDecimal("100000"))
                .build();
        testOrder.getItems().add(item);
    }

    // ==================== Helper Methods ====================

    private Payment buildCompletedPayment(Long id, BigDecimal amount, PaymentMethod method) {
        Payment payment = Payment.builder()
                .order(testOrder)
                .method(method)
                .status(PaymentStatus.COMPLETED)
                .amount(amount)
                .tipAmount(BigDecimal.ZERO)
                .refundedAmount(BigDecimal.ZERO)
                .transactionId("TXN-" + id)
                .paidAt(OffsetDateTime.now(ZoneOffset.UTC))
                .completedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        payment.setId(id);
        return payment;
    }

    private PaymentRequestDTO buildPaymentRequest(PaymentMethod method, BigDecimal amount) {
        return PaymentRequestDTO.builder()
                .method(method)
                .amount(amount)
                .processedBy("cashier1")
                .build();
    }

    private void stubIdempotencyForSuccess() {
        when(idempotencyService.getProcessedOrderForTransaction(any())).thenReturn(null);
        when(idempotencyService.acquireOrderPaymentLock(anyLong())).thenReturn(true);
    }

    private void stubOrderFound() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
    }

    private void stubPaymentSave() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            if (p.getId() == null) {
                p.setId(100L);
            }
            return p;
        });
    }

    private void stubOrderSave() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ==================== processPOSPayment Tests ====================

    @Nested
    @DisplayName("processPOSPayment")
    class ProcessPOSPaymentTests {

        @Test
        @DisplayName("1. Cash payment success with correct change calculation")
        void cashPayment_success_withCorrectChange() {
            stubIdempotencyForSuccess();
            stubOrderFound();
            stubPaymentSave();
            stubOrderSave();
            when(paymentRepository.findByOrderId(1L)).thenAnswer(inv -> testOrder.getPayments());

            PaymentRequestDTO request = PaymentRequestDTO.builder()
                    .method(PaymentMethod.CASH)
                    .amount(new BigDecimal("100000"))
                    .tipAmount(BigDecimal.ZERO)
                    .amountTendered(new BigDecimal("120000"))
                    .processedBy("cashier1")
                    .build();

            PaymentResponseDTO response = paymentService.processPOSPayment(1L, request);

            assertThat(response).isNotNull();
            assertThat(response.getMethod()).isEqualTo(PaymentMethod.CASH);
            assertThat(response.getAmount()).isEqualByComparingTo("100000");
            assertThat(response.getAmountTendered()).isEqualByComparingTo("120000");
            assertThat(response.getChangeDue()).isEqualByComparingTo("20000");
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

            verify(paymentRepository).save(paymentCaptor.capture());
            Payment saved = paymentCaptor.getValue();
            assertThat(saved.getChangeDue()).isEqualByComparingTo("20000");
            verify(idempotencyService).releaseOrderPaymentLock(1L);
        }

        @Test
        @DisplayName("2. Card payment success")
        void cardPayment_success() {
            stubIdempotencyForSuccess();
            stubOrderFound();
            stubPaymentSave();
            stubOrderSave();
            when(paymentRepository.findByOrderId(1L)).thenAnswer(inv -> testOrder.getPayments());

            PaymentRequestDTO request = PaymentRequestDTO.builder()
                    .method(PaymentMethod.CARD)
                    .amount(new BigDecimal("100000"))
                    .transactionId("CARD-12345")
                    .paymentGateway("STRIPE")
                    .processedBy("cashier1")
                    .build();

            PaymentResponseDTO response = paymentService.processPOSPayment(1L, request);

            assertThat(response).isNotNull();
            assertThat(response.getMethod()).isEqualTo(PaymentMethod.CARD);
            assertThat(response.getTransactionId()).isEqualTo("CARD-12345");
            assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);

            verify(idempotencyService).registerSuccessfulPayment("CARD-12345", 1L);
        }

        @Test
        @DisplayName("a fully-paid order is put on the kitchen board (till-paid takeaway reaches the KDS)")
        void fullPayment_sendsOrderToKitchen() {
            stubIdempotencyForSuccess();
            stubOrderFound();
            stubPaymentSave();
            stubOrderSave();
            when(paymentRepository.findByOrderId(1L)).thenAnswer(inv -> testOrder.getPayments());

            paymentService.processPOSPayment(1L, buildPaymentRequest(PaymentMethod.CARD, new BigDecimal("100000")));

            // Idempotent: a dine-in order sent earlier already has a ticket; a till-paid takeaway gets one now.
            verify(kitchenOrderService).createKitchenOrderIfAbsent(testOrder);
        }

        @Test
        @DisplayName("a partial payment does not create a kitchen ticket (order not settled yet)")
        void partialPayment_doesNotSendToKitchen() {
            stubIdempotencyForSuccess();
            stubOrderFound();
            stubPaymentSave();
            stubOrderSave();
            when(paymentRepository.findByOrderId(1L)).thenAnswer(inv -> testOrder.getPayments());

            // 40k of a 100k order — not fully paid, so nothing is fired to the kitchen.
            paymentService.processPOSPayment(1L, buildPaymentRequest(PaymentMethod.CARD, new BigDecimal("40000")));

            verify(kitchenOrderService, never()).createKitchenOrderIfAbsent(any(Order.class));
        }

        @Test
        @DisplayName("3. Payment with tip updates order grand total")
        void paymentWithTip_updatesGrandTotal() {
            stubIdempotencyForSuccess();
            stubOrderFound();
            stubPaymentSave();
            stubOrderSave();
            when(paymentRepository.findByOrderId(1L)).thenAnswer(inv -> testOrder.getPayments());

            PaymentRequestDTO request = PaymentRequestDTO.builder()
                    .method(PaymentMethod.CASH)
                    .amount(new BigDecimal("100000"))
                    .tipAmount(new BigDecimal("10000"))
                    .amountTendered(new BigDecimal("110000"))
                    .processedBy("cashier1")
                    .build();

            paymentService.processPOSPayment(1L, request);

            verify(orderRepository).save(orderCaptor.capture());
            Order savedOrder = orderCaptor.getValue();
            assertThat(savedOrder.getTipAmount()).isEqualByComparingTo("10000");
            assertThat(savedOrder.getGrandTotal()).isEqualByComparingTo("110000");
        }

        @Test
        @DisplayName("4. Split payment sets split number")
        void splitPayment_setsNumber() {
            stubIdempotencyForSuccess();
            stubOrderFound();
            stubPaymentSave();
            stubOrderSave();
            when(paymentRepository.findByOrderId(1L)).thenAnswer(inv -> testOrder.getPayments());

            PaymentRequestDTO request = PaymentRequestDTO.builder()
                    .method(PaymentMethod.CARD)
                    .amount(new BigDecimal("50000"))
                    .splitNumber(1)
                    .processedBy("cashier1")
                    .build();

            PaymentResponseDTO response = paymentService.processPOSPayment(1L, request);

            assertThat(response.getSplitNumber()).isEqualTo(1);

            verify(paymentRepository).save(paymentCaptor.capture());
            assertThat(paymentCaptor.getValue().getSplitNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("5. Full payment sets order DELIVERED and releases tables")
        void fullPayment_setsOrderDelivered_releasesTables() {
            stubIdempotencyForSuccess();
            stubOrderFound();
            stubPaymentSave();
            stubOrderSave();
            when(paymentRepository.findByOrderId(1L)).thenAnswer(inv -> testOrder.getPayments());

            PaymentRequestDTO request = PaymentRequestDTO.builder()
                    .method(PaymentMethod.CARD)
                    .amount(new BigDecimal("100000"))
                    .processedBy("cashier1")
                    .build();

            PaymentResponseDTO response = paymentService.processPOSPayment(1L, request);

            verify(orderRepository).save(orderCaptor.capture());
            Order savedOrder = orderCaptor.getValue();
            assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.DELIVERED);
            assertThat(savedOrder.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(savedOrder.getCompletedAt()).isNotNull();

            verify(posTableService).releaseTablesForOrder(any(Order.class));
            verify(revenueRecordingService).recordRevenueWithRetry(any(Order.class));
            // Full payment is the paid-side qualifying moment for the completion chain (FUNC-15).
            verify(orderCompletionEvents).publishIfQualified(any(Order.class));
            assertThat(response.isOrderFullyPaid()).isTrue();
        }

        @Test
        @DisplayName("6. Partial payment does not mark order as fully paid")
        void partialPayment_orderNotFullyPaid() {
            stubIdempotencyForSuccess();
            stubOrderFound();
            stubPaymentSave();
            stubOrderSave();
            when(paymentRepository.findByOrderId(1L)).thenAnswer(inv -> testOrder.getPayments());

            PaymentRequestDTO request = PaymentRequestDTO.builder()
                    .method(PaymentMethod.CARD)
                    .amount(new BigDecimal("50000"))
                    .processedBy("cashier1")
                    .build();

            PaymentResponseDTO response = paymentService.processPOSPayment(1L, request);

            verify(orderRepository).save(orderCaptor.capture());
            Order savedOrder = orderCaptor.getValue();
            assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PREPARING);
            assertThat(savedOrder.getPaymentStatus()).isNotEqualTo(PaymentStatus.COMPLETED);

            verify(posTableService, never()).releaseTablesForOrder(any(Order.class));
            assertThat(response.isOrderFullyPaid()).isFalse();
            assertThat(response.getRemainingBalance()).isEqualByComparingTo("50000");
        }

        @Test
        @DisplayName("7. Order not found throws PaymentTransactionException")
        void orderNotFound_throws() {
            stubIdempotencyForSuccess();
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());

            PaymentRequestDTO request = buildPaymentRequest(PaymentMethod.CASH, new BigDecimal("50000"));

            assertThatThrownBy(() -> paymentService.processPOSPayment(99L, request))
                    .isInstanceOf(PaymentTransactionException.class)
                    .hasMessageContaining("Order not found");

            verify(idempotencyService).releaseOrderPaymentLock(99L);
        }

        @Test
        @DisplayName("8. Cancelled order throws PaymentTransactionException")
        void orderCancelled_throws() {
            testOrder.setStatus(OrderStatus.CANCELLED);
            stubIdempotencyForSuccess();
            stubOrderFound();

            PaymentRequestDTO request = buildPaymentRequest(PaymentMethod.CASH, new BigDecimal("50000"));

            assertThatThrownBy(() -> paymentService.processPOSPayment(1L, request))
                    .isInstanceOf(PaymentTransactionException.class)
                    .hasMessageContaining("cancelled");

            verify(idempotencyService).releaseOrderPaymentLock(1L);
        }

        @Test
        @DisplayName("9. Already fully paid order returns existing payment summary")
        void orderAlreadyFullyPaid_returnsSummary() {
            Payment existingPayment = buildCompletedPayment(10L, new BigDecimal("100000"), PaymentMethod.CASH);
            testOrder.getPayments().add(existingPayment);

            stubOrderFound();
            when(paymentRepository.findByOrderId(1L)).thenReturn(List.of(existingPayment));

            PaymentRequestDTO request = buildPaymentRequest(PaymentMethod.CASH, new BigDecimal("10000"));

            PaymentResponseDTO response = paymentService.processPOSPayment(1L, request);

            assertThat(response).isNotNull();
            assertThat(response.isOrderFullyPaid()).isTrue();
            assertThat(response.getOrderId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("10. Duplicate transaction ID returns existing payment via idempotency")
        void duplicateTransaction_idempotencyBlocks() {
            when(idempotencyService.getProcessedOrderForTransaction("TXN-DUPLICATE"))
                    .thenReturn(1L);
            stubOrderFound();
            when(paymentRepository.findByOrderId(1L)).thenReturn(List.of(
                    buildCompletedPayment(10L, new BigDecimal("100000"), PaymentMethod.CARD)
            ));

            PaymentRequestDTO request = PaymentRequestDTO.builder()
                    .method(PaymentMethod.CARD)
                    .amount(new BigDecimal("100000"))
                    .transactionId("TXN-DUPLICATE")
                    .processedBy("cashier1")
                    .build();

            PaymentResponseDTO response = paymentService.processPOSPayment(1L, request);

            assertThat(response).isNotNull();
            assertThat(response.getOrderId()).isEqualTo(1L);

            // Should NOT acquire lock or save any new payment
            verify(idempotencyService, never()).acquireOrderPaymentLock(anyLong());
            verify(paymentRepository, never()).save(any(Payment.class));
        }
    }

    // ==================== processPOSRefund Tests ====================

    @Nested
    @DisplayName("processPOSRefund")
    class ProcessPOSRefundTests {

        @Test
        @DisplayName("11. Full refund success")
        void fullRefund_success() {
            Payment completedPayment = buildCompletedPayment(10L, new BigDecimal("100000"), PaymentMethod.CARD);
            testOrder.getPayments().add(completedPayment);

            stubOrderFound();
            stubPaymentSave();
            stubOrderSave();
            when(paymentRepository.findByOrderIdAndStatus(1L, PaymentStatus.COMPLETED))
                    .thenReturn(List.of(completedPayment));
            when(paymentRepository.findByOrderId(1L)).thenReturn(testOrder.getPayments());
            when(auditService.logFinancialOperation(any(), anyLong(), any(), any(), any(), any(), any()))
                    .thenReturn(null);

            RefundRequestDTO request = RefundRequestDTO.builder()
                    .type(RefundRequestDTO.RefundType.FULL)
                    .reason("Customer complaint")
                    .processedBy("manager1")
                    .build();

            PaymentResponseDTO response = paymentService.processPOSRefund(1L, request);

            assertThat(response).isNotNull();

            verify(paymentRepository).save(paymentCaptor.capture());
            Payment refundedPayment = paymentCaptor.getValue();
            assertThat(refundedPayment.getRefundedAmount()).isEqualByComparingTo("100000");
            assertThat(refundedPayment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
            assertThat(refundedPayment.getRefundReason()).isEqualTo("Customer complaint");
            assertThat(refundedPayment.getRefundedAt()).isNotNull();
            assertThat(refundedPayment.getProcessedBy()).isEqualTo("manager1");

            verify(auditService).logFinancialOperation(
                    eq(AuditAction.REFUND_COMPLETED),
                    eq(1L),
                    anyString(),
                    any(),
                    any(BigDecimal.class),
                    eq("UZS"),
                    anyString()
            );
        }

        @Test
        @DisplayName("12. Partial refund success")
        void partialRefund_success() {
            Payment completedPayment = buildCompletedPayment(10L, new BigDecimal("100000"), PaymentMethod.CARD);
            testOrder.getPayments().add(completedPayment);

            stubOrderFound();
            stubPaymentSave();
            stubOrderSave();
            when(paymentRepository.findByOrderIdAndStatus(1L, PaymentStatus.COMPLETED))
                    .thenReturn(List.of(completedPayment));
            when(paymentRepository.findByOrderId(1L)).thenReturn(testOrder.getPayments());
            when(auditService.logFinancialOperation(any(), anyLong(), any(), any(), any(), any(), any()))
                    .thenReturn(null);

            RefundRequestDTO request = RefundRequestDTO.builder()
                    .type(RefundRequestDTO.RefundType.PARTIAL)
                    .amount(new BigDecimal("30000"))
                    .reason("Overcharged")
                    .processedBy("manager1")
                    .build();

            PaymentResponseDTO response = paymentService.processPOSRefund(1L, request);

            assertThat(response).isNotNull();

            verify(paymentRepository).save(paymentCaptor.capture());
            Payment refundedPayment = paymentCaptor.getValue();
            assertThat(refundedPayment.getRefundedAmount()).isEqualByComparingTo("30000");
            assertThat(refundedPayment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        }

        @Test
        @DisplayName("13. Refund exceeding total paid throws IllegalArgumentException")
        void refundExceedsPaid_throws() {
            Payment completedPayment = buildCompletedPayment(10L, new BigDecimal("50000"), PaymentMethod.CARD);
            testOrder.getPayments().add(completedPayment);

            stubOrderFound();

            RefundRequestDTO request = RefundRequestDTO.builder()
                    .type(RefundRequestDTO.RefundType.PARTIAL)
                    .amount(new BigDecimal("60000"))
                    .reason("Too much")
                    .processedBy("manager1")
                    .build();

            assertThatThrownBy(() -> paymentService.processPOSRefund(1L, request))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("cannot exceed total paid");
        }
    }

    // ==================== voidOrder Tests ====================

    @Nested
    @DisplayName("voidOrder")
    class VoidOrderTests {

        @Test
        @DisplayName("14. Void order success - cancels order and voids all payments")
        void voidOrder_success_cancelsOrder() {
            Payment completedPayment = buildCompletedPayment(10L, new BigDecimal("100000"), PaymentMethod.CARD);
            testOrder.getPayments().add(completedPayment);

            stubOrderFound();
            stubPaymentSave();
            stubOrderSave();
            when(paymentRepository.findByOrderId(1L)).thenReturn(List.of(completedPayment));

            PaymentResponseDTO response = paymentService.voidOrder(1L, "Wrong order", "manager1");

            assertThat(response).isNotNull();

            verify(paymentRepository).save(paymentCaptor.capture());
            Payment voidedPayment = paymentCaptor.getValue();
            assertThat(voidedPayment.getStatus()).isEqualTo(PaymentStatus.VOIDED);
            assertThat(voidedPayment.getRefundedAmount()).isEqualByComparingTo("100000");
            assertThat(voidedPayment.getRefundReason()).isEqualTo("Wrong order");
            assertThat(voidedPayment.getRefundedAt()).isNotNull();

            verify(orderRepository).save(orderCaptor.capture());
            Order savedOrder = orderCaptor.getValue();
            assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(savedOrder.getPaymentStatus()).isEqualTo(PaymentStatus.VOIDED);
            assertThat(savedOrder.getCancelledAt()).isNotNull();
            assertThat(savedOrder.getCancelledBy()).isEqualTo("manager1");
            assertThat(savedOrder.getCancellationReason()).isEqualTo("Wrong order");
        }

        @Test
        @DisplayName("15. Void order not found throws PaymentTransactionException")
        void voidOrder_notFound_throws() {
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.voidOrder(99L, "reason", "manager1"))
                    .isInstanceOf(PaymentTransactionException.class)
                    .hasMessageContaining("Order not found");
        }
    }

    // ==================== addTip Tests ====================

    @Nested
    @DisplayName("addTip")
    class AddTipTests {

        @Test
        @DisplayName("16. Add tip success updates grand total")
        void addTip_success_updatesGrandTotal() {
            stubOrderFound();
            stubOrderSave();
            when(paymentRepository.findByOrderId(1L)).thenReturn(testOrder.getPayments());

            PaymentResponseDTO response = paymentService.addTip(1L, new BigDecimal("20000"));

            assertThat(response).isNotNull();

            verify(orderRepository).save(orderCaptor.capture());
            Order savedOrder = orderCaptor.getValue();
            assertThat(savedOrder.getTipAmount()).isEqualByComparingTo("20000");
            // grandTotal = total(100000) + tip(20000) = 120000
            assertThat(savedOrder.getGrandTotal()).isEqualByComparingTo("120000");
        }

        @Test
        @DisplayName("17. Add tip with null amount throws IllegalArgumentException")
        void addTip_nullAmount_throws() {
            stubOrderFound();

            assertThatThrownBy(() -> paymentService.addTip(1L, null))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("greater than 0");
        }

        @Test
        @DisplayName("17b. Add tip with zero amount throws IllegalArgumentException")
        void addTip_zeroAmount_throws() {
            stubOrderFound();

            assertThatThrownBy(() -> paymentService.addTip(1L, BigDecimal.ZERO))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("greater than 0");
        }

        @Test
        @DisplayName("17c. Add tip with negative amount throws IllegalArgumentException")
        void addTip_negativeAmount_throws() {
            stubOrderFound();

            assertThatThrownBy(() -> paymentService.addTip(1L, new BigDecimal("-5000")))
                    .isInstanceOf(BadRequestException.class)
                    .hasMessageContaining("greater than 0");
        }
    }

    // ==================== getPOSPaymentSummary Tests ====================

    @Nested
    @DisplayName("getPOSPaymentSummary")
    class GetPOSPaymentSummaryTests {

        @Test
        @DisplayName("18. Summary with multiple payments shows correct aggregation")
        void summary_withMultiplePayments() {
            Payment payment1 = buildCompletedPayment(10L, new BigDecimal("60000"), PaymentMethod.CASH);
            payment1.setSplitNumber(1);
            Payment payment2 = buildCompletedPayment(11L, new BigDecimal("40000"), PaymentMethod.CARD);
            payment2.setSplitNumber(2);

            testOrder.getPayments().add(payment1);
            testOrder.getPayments().add(payment2);

            stubOrderFound();
            when(paymentRepository.findByOrderId(1L)).thenReturn(List.of(payment1, payment2));

            PaymentResponseDTO response = paymentService.getPOSPaymentSummary(1L);

            assertThat(response).isNotNull();
            assertThat(response.getOrderId()).isEqualTo(1L);
            assertThat(response.getOrderNumber()).isEqualTo("W-TEST-001");
            assertThat(response.getOrderTotal()).isEqualByComparingTo("100000");
            assertThat(response.getOrderSubtotal()).isEqualByComparingTo("100000");
            assertThat(response.getTotalPaid()).isEqualByComparingTo("100000");
            assertThat(response.isOrderFullyPaid()).isTrue();
            assertThat(response.getRemainingBalance()).isEqualByComparingTo("0");
            assertThat(response.getAllPayments()).hasSize(2);

            PaymentResponseDTO.PaymentSummary summary1 = response.getAllPayments().get(0);
            assertThat(summary1.getMethod()).isEqualTo(PaymentMethod.CASH);
            assertThat(summary1.getAmount()).isEqualByComparingTo("60000");
            assertThat(summary1.getSplitNumber()).isEqualTo(1);

            PaymentResponseDTO.PaymentSummary summary2 = response.getAllPayments().get(1);
            assertThat(summary2.getMethod()).isEqualTo(PaymentMethod.CARD);
            assertThat(summary2.getAmount()).isEqualByComparingTo("40000");
            assertThat(summary2.getSplitNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("19. Summary for non-existent order throws exception")
        void summary_orderNotFound_throws() {
            when(orderRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPOSPaymentSummary(99L))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Order not found");
        }
    }

    // ==================== CRUD / deletePayment Tests ====================

    @Nested
    @DisplayName("deletePayment")
    class DeletePaymentTests {

        @Test
        @DisplayName("20. Delete payment performs soft delete with audit trail")
        void deletePayment_softDeletes() {
            Payment payment = buildCompletedPayment(10L, new BigDecimal("100000"), PaymentMethod.CARD);
            payment.setOrder(testOrder);

            when(paymentRepository.findByIdAndOrderId(10L, 1L)).thenReturn(Optional.of(payment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));
            when(auditService.logFinancialOperation(any(), anyLong(), any(), any(), any(), any(), any()))
                    .thenReturn(null);

            paymentService.deletePayment(1L, 10L, "admin");

            verify(paymentRepository).save(paymentCaptor.capture());
            Payment deletedPayment = paymentCaptor.getValue();
            assertThat(deletedPayment.isDeleted()).isTrue();
            assertThat(deletedPayment.getDeletedAt()).isNotNull();
            assertThat(deletedPayment.getDeletedBy()).isEqualTo("admin");

            verify(auditService).logFinancialOperation(
                    eq(AuditAction.PAYMENT_CANCELLED),
                    eq(1L),
                    eq("W-TEST-001"),
                    eq(1L),
                    any(BigDecimal.class),
                    eq("UZS"),
                    anyString()
            );
        }

        @Test
        @DisplayName("20b. Delete already-deleted payment throws exception")
        void deletePayment_alreadyDeleted_throws() {
            Payment payment = buildCompletedPayment(10L, new BigDecimal("100000"), PaymentMethod.CARD);
            payment.setOrder(testOrder);
            payment.softDelete("someone");

            when(paymentRepository.findByIdAndOrderId(10L, 1L)).thenReturn(Optional.of(payment));

            assertThatThrownBy(() -> paymentService.deletePayment(1L, 10L, "admin"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("already been deleted");
        }

        @Test
        @DisplayName("20c. Delete non-existent payment throws exception")
        void deletePayment_notFound_throws() {
            when(paymentRepository.findByIdAndOrderId(99L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.deletePayment(1L, 99L, "admin"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Payment not found");
        }
    }

    // ==================== Additional Edge Case Tests ====================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Lock is released even when payment processing fails")
        void lockReleasedOnFailure() {
            when(idempotencyService.getProcessedOrderForTransaction(any())).thenReturn(null);
            when(idempotencyService.acquireOrderPaymentLock(1L)).thenReturn(true);
            when(orderRepository.findById(1L)).thenReturn(Optional.empty());

            PaymentRequestDTO request = buildPaymentRequest(PaymentMethod.CASH, new BigDecimal("100000"));

            try {
                paymentService.processPOSPayment(1L, request);
            } catch (PaymentTransactionException ignored) {
                // expected
            }

            verify(idempotencyService).releaseOrderPaymentLock(1L);
        }

        @Test
        @DisplayName("Concurrent payment lock not acquired throws exception")
        void concurrentLock_throws() {
            when(idempotencyService.getProcessedOrderForTransaction(any())).thenReturn(null);
            when(idempotencyService.acquireOrderPaymentLock(1L)).thenReturn(false);

            PaymentRequestDTO request = buildPaymentRequest(PaymentMethod.CASH, new BigDecimal("100000"));

            assertThatThrownBy(() -> paymentService.processPOSPayment(1L, request))
                    .isInstanceOf(PaymentTransactionException.class)
                    .hasMessageContaining("Another payment is being processed");
        }

        @Test
        @DisplayName("Cash tendered less than total payment throws exception")
        void cashTenderedLessThanAmount_throws() {
            stubIdempotencyForSuccess();
            stubOrderFound();

            PaymentRequestDTO request = PaymentRequestDTO.builder()
                    .method(PaymentMethod.CASH)
                    .amount(new BigDecimal("100000"))
                    .amountTendered(new BigDecimal("50000"))
                    .processedBy("cashier1")
                    .build();

            assertThatThrownBy(() -> paymentService.processPOSPayment(1L, request))
                    .isInstanceOf(PaymentTransactionException.class)
                    .hasMessageContaining("Amount tendered is less than payment amount");
        }
    }
}
