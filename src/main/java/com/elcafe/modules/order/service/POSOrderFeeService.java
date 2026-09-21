package com.elcafe.modules.order.service;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Service responsible for fee-related operations on orders.
 * Handles service fees and entry fees.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class POSOrderFeeService {

    private final OrderRepository orderRepository;

    /**
     * Apply service fee to an order by percentage
     */
    @Transactional
    public Order applyServiceFee(Long orderId, BigDecimal serviceFeePercent) {
        log.info("Applying service fee to order {}: percent={}", orderId, serviceFeePercent);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        // Validate service fee percent
        if (serviceFeePercent == null || serviceFeePercent.compareTo(BigDecimal.ZERO) < 0) {
            serviceFeePercent = BigDecimal.ZERO;
        }
        if (serviceFeePercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Service fee percent cannot exceed 100%");
        }

        order.setServiceFeePercent(serviceFeePercent);

        // Calculate service fee amount from subtotal
        BigDecimal serviceFee = BigDecimal.ZERO;
        if (serviceFeePercent.compareTo(BigDecimal.ZERO) > 0) {
            serviceFee = order.getSubtotal().multiply(serviceFeePercent)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        order.setServiceFee(serviceFee);

        recalculateTotal(order);

        Order savedOrder = orderRepository.save(order);
        log.info("Service fee applied to order {}: fee={}, newTotal={}", orderId, serviceFee, order.getTotal());

        return savedOrder;
    }

    /**
     * Apply service fee to an order by fixed amount
     */
    @Transactional
    public Order applyServiceFeeAmount(Long orderId, BigDecimal serviceFeeAmount) {
        log.info("Applying service fee amount to order {}: amount={}", orderId, serviceFeeAmount);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        // Validate service fee amount
        if (serviceFeeAmount == null || serviceFeeAmount.compareTo(BigDecimal.ZERO) < 0) {
            serviceFeeAmount = BigDecimal.ZERO;
        }

        // Calculate percentage for display purposes
        BigDecimal serviceFeePercent = BigDecimal.ZERO;
        if (serviceFeeAmount.compareTo(BigDecimal.ZERO) > 0 && order.getSubtotal().compareTo(BigDecimal.ZERO) > 0) {
            serviceFeePercent = serviceFeeAmount.multiply(BigDecimal.valueOf(100))
                    .divide(order.getSubtotal(), 2, RoundingMode.HALF_UP);
        }

        order.setServiceFee(serviceFeeAmount);
        order.setServiceFeePercent(serviceFeePercent);

        recalculateTotal(order);

        Order savedOrder = orderRepository.save(order);
        log.info("Service fee amount applied to order {}: fee={}, newTotal={}", orderId, serviceFeeAmount, order.getTotal());

        return savedOrder;
    }

    /**
     * Apply entry fee to an order
     */
    @Transactional
    public Order applyEntryFee(Long orderId, BigDecimal entryFeeAmount) {
        log.info("Applying entry fee to order {}: amount={}", orderId, entryFeeAmount);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        // Validate entry fee amount
        if (entryFeeAmount == null || entryFeeAmount.compareTo(BigDecimal.ZERO) < 0) {
            entryFeeAmount = BigDecimal.ZERO;
        }

        order.setEntryFee(entryFeeAmount);

        recalculateTotal(order);

        Order savedOrder = orderRepository.save(order);
        log.info("Entry fee applied to order {}: fee={}, newTotal={}", orderId, entryFeeAmount, order.getTotal());

        return savedOrder;
    }

    /**
     * Recalculate order total including all fees and discounts
     */
    private void recalculateTotal(Order order) {
        BigDecimal serviceFee = order.getServiceFee() != null ? order.getServiceFee() : BigDecimal.ZERO;
        BigDecimal entryFee = order.getEntryFee() != null ? order.getEntryFee() : BigDecimal.ZERO;
        BigDecimal discount = order.getDiscount() != null ? order.getDiscount() : BigDecimal.ZERO;

        BigDecimal total = order.getSubtotal()
                .add(order.getTax())
                .add(order.getDeliveryFee())
                .add(serviceFee)
                .add(entryFee)
                .subtract(discount);
        order.setTotal(total);

        // Update grand total (total + tip)
        BigDecimal tipAmount = order.getTipAmount() != null ? order.getTipAmount() : BigDecimal.ZERO;
        order.setGrandTotal(total.add(tipAmount));
    }
}
