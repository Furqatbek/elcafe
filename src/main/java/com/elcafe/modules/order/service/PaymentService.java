package com.elcafe.modules.order.service;

import com.elcafe.modules.order.dto.*;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.Payment;
import com.elcafe.modules.order.enums.PaymentMethod;
import com.elcafe.modules.order.enums.PaymentStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.order.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getAllPayments(Pageable pageable) {
        return paymentRepository.findAll(pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long orderId, Long paymentId) {
        Payment payment = paymentRepository.findByIdAndOrderId(paymentId, orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + paymentId + " for order: " + orderId));
        return toResponse(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found for order: " + orderId));
        return toResponse(payment);
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPaymentsByStatus(PaymentStatus status, Pageable pageable) {
        return paymentRepository.findByStatus(status, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPaymentsByMethod(PaymentMethod method, Pageable pageable) {
        return paymentRepository.findByMethod(method, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByTransactionId(String transactionId) {
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new RuntimeException("Payment not found with transaction ID: " + transactionId));
        return toResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByStatusAndDateRange(
            PaymentStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        return paymentRepository.findByStatusAndDateRange(status, startDate, endDate).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PaymentResponse createPayment(Long orderId, CreatePaymentRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));

        // Check if payment already exists for this order
        if (paymentRepository.findByOrderId(orderId).isPresent()) {
            throw new RuntimeException("Payment already exists for order: " + orderId);
        }

        // Validate transaction ID uniqueness if provided
        if (request.getTransactionId() != null &&
            paymentRepository.existsByTransactionId(request.getTransactionId())) {
            throw new RuntimeException("Payment with transaction ID '" + request.getTransactionId() + "' already exists");
        }

        Payment payment = Payment.builder()
                .order(order)
                .method(request.getMethod())
                .status(request.getStatus() != null ? request.getStatus() : PaymentStatus.PENDING)
                .amount(request.getAmount())
                .transactionId(request.getTransactionId())
                .paymentGateway(request.getPaymentGateway())
                .paymentDetails(request.getPaymentDetails())
                .build();

        // Set paidAt if status is COMPLETED
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            payment.setPaidAt(LocalDateTime.now());
        }

        Payment saved = paymentRepository.save(payment);
        log.info("Created payment: {} for order: {}", saved.getId(), orderId);
        return toResponse(saved);
    }

    @Transactional
    public PaymentResponse updatePayment(Long orderId, Long paymentId, UpdatePaymentRequest request) {
        Payment payment = paymentRepository.findByIdAndOrderId(paymentId, orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + paymentId + " for order: " + orderId));

        if (request.getMethod() != null) payment.setMethod(request.getMethod());

        if (request.getStatus() != null) {
            PaymentStatus oldStatus = payment.getStatus();
            payment.setStatus(request.getStatus());

            // Set paidAt when status changes to COMPLETED
            if (request.getStatus() == PaymentStatus.COMPLETED && oldStatus != PaymentStatus.COMPLETED) {
                payment.setPaidAt(LocalDateTime.now());
            }
            // Clear paidAt if status is changed from COMPLETED to something else
            else if (request.getStatus() != PaymentStatus.COMPLETED && oldStatus == PaymentStatus.COMPLETED) {
                payment.setPaidAt(null);
            }
        }

        if (request.getAmount() != null) payment.setAmount(request.getAmount());

        if (request.getTransactionId() != null) {
            if (!payment.getTransactionId().equals(request.getTransactionId()) &&
                paymentRepository.existsByTransactionId(request.getTransactionId())) {
                throw new RuntimeException("Payment with transaction ID '" + request.getTransactionId() + "' already exists");
            }
            payment.setTransactionId(request.getTransactionId());
        }

        if (request.getPaymentGateway() != null) payment.setPaymentGateway(request.getPaymentGateway());
        if (request.getPaymentDetails() != null) payment.setPaymentDetails(request.getPaymentDetails());

        Payment updated = paymentRepository.save(payment);
        log.info("Updated payment: {} for order: {}", updated.getId(), orderId);
        return toResponse(updated);
    }

    @Transactional
    public void deletePayment(Long orderId, Long paymentId) {
        Payment payment = paymentRepository.findByIdAndOrderId(paymentId, orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found with id: " + paymentId + " for order: " + orderId));

        paymentRepository.delete(payment);
        log.info("Deleted payment: {} for order: {}", payment.getId(), orderId);
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .orderId(payment.getOrder().getId())
                .orderNumber(payment.getOrder().getOrderNumber())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .transactionId(payment.getTransactionId())
                .paymentGateway(payment.getPaymentGateway())
                .paymentDetails(payment.getPaymentDetails())
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
