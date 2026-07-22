package com.elcafe.modules.pos.tax.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.pos.tax.dto.*;
import com.elcafe.modules.pos.tax.entity.TaxExemptionLog;
import com.elcafe.modules.pos.tax.entity.TaxExemptionType;
import com.elcafe.modules.pos.tax.repository.TaxExemptionLogRepository;
import com.elcafe.modules.pos.tax.repository.TaxExemptionTypeRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Service for managing tax exemptions for customers and orders.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaxExemptionService {

    private final TaxExemptionTypeRepository exemptionTypeRepository;
    private final TaxExemptionLogRepository exemptionLogRepository;
    private final RestaurantRepository restaurantRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    /**
     * Create a tax exemption type.
     */
    @Transactional
    public TaxExemptionType createExemptionType(Long restaurantId, CreateTaxExemptionTypeRequest request) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new IllegalArgumentException("Restaurant not found"));

        if (exemptionTypeRepository.existsByRestaurantIdAndName(restaurantId, request.getName())) {
            throw new IllegalArgumentException("Exemption type with this name already exists");
        }

        TaxExemptionType type = TaxExemptionType.builder()
            .restaurant(restaurant)
            .name(request.getName())
            .description(request.getDescription())
            .exemptionCode(request.getExemptionCode())
            .requiresDocumentation(request.getRequiresDocumentation())
            .isActive(true)
            .build();

        log.info("Created tax exemption type {} for restaurant {}", request.getName(), restaurantId);
        return exemptionTypeRepository.save(type);
    }

    /**
     * Get all active exemption types for a restaurant.
     */
    public List<TaxExemptionType> getExemptionTypes(Long restaurantId) {
        return exemptionTypeRepository.findByRestaurantIdAndIsActiveTrue(restaurantId);
    }

    /**
     * Mark a customer as tax exempt.
     */
    @Transactional
    public Customer setCustomerTaxExempt(Long customerId, SetCustomerTaxExemptRequest request) {
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Customer not found"));

        TaxExemptionType exemptionType = null;
        if (request.getExemptionTypeId() != null) {
            exemptionType = exemptionTypeRepository.findById(request.getExemptionTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Exemption type not found"));
        }

        customer.setIsTaxExempt(true);
        customer.setTaxExemptionTypeId(request.getExemptionTypeId());
        customer.setTaxExemptionNumber(request.getExemptionNumber());
        customer.setTaxExemptionExpiresAt(request.getExpiresAt());

        log.info("Customer {} marked as tax exempt: {}", customerId, request.getExemptionNumber());
        return customerRepository.save(customer);
    }

    /**
     * Remove tax exempt status from customer.
     */
    @Transactional
    public Customer removeCustomerTaxExempt(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Customer not found"));

        customer.setIsTaxExempt(false);
        customer.setTaxExemptionTypeId(null);
        customer.setTaxExemptionNumber(null);
        customer.setTaxExemptionExpiresAt(null);

        log.info("Customer {} tax exempt status removed", customerId);
        return customerRepository.save(customer);
    }

    /**
     * Apply tax exemption to an order.
     */
    @Transactional
    public TaxExemptionResult applyOrderTaxExemption(Long orderId, ApplyTaxExemptionRequest request,
                                                      Long operatorId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        User operator = userRepository.findById(operatorId)
            .orElseThrow(() -> new IllegalArgumentException("Operator not found"));

        TaxExemptionType exemptionType = null;
        if (request.getExemptionTypeId() != null) {
            exemptionType = exemptionTypeRepository.findById(request.getExemptionTypeId())
                .orElseThrow(() -> new IllegalArgumentException("Exemption type not found"));
        }

        // Calculate tax being exempted
        BigDecimal taxExempted = order.getTax();

        // Update order
        order.setIsTaxExempt(true);
        order.setTaxExemptionTypeId(request.getExemptionTypeId());
        order.setTaxExemptionNumber(request.getExemptionNumber());
        order.setTaxExemptionReason(request.getReason());

        // Recalculate totals (remove tax)
        BigDecimal newTotal = order.getSubtotal()
            .add(order.getDeliveryFee())
            .add(order.getServiceFee())
            .add(order.getEntryFee())
            .subtract(order.getDiscount())
            .subtract(order.getBonusUsed());

        order.setTax(BigDecimal.ZERO);
        order.setTotal(newTotal);
        order.setGrandTotal(newTotal.add(order.getTipAmount()));

        orderRepository.save(order);

        // Log the exemption
        TaxExemptionLog exemptionLog = TaxExemptionLog.builder()
            .restaurant(order.getRestaurant())
            .order(order)
            .customer(order.getCustomer())
            .exemptionType(exemptionType)
            .exemptionNumber(request.getExemptionNumber())
            .taxAmountExempted(taxExempted)
            .appliedBy(operator)
            .reason(request.getReason())
            .build();
        exemptionLogRepository.save(exemptionLog);

        log.info("Tax exemption applied to order {}: {} exempted", orderId, taxExempted);

        return TaxExemptionResult.builder()
            .orderId(orderId)
            .taxExempted(taxExempted)
            .newTotal(newTotal)
            .exemptionNumber(request.getExemptionNumber())
            .build();
    }

    /**
     * Remove tax exemption from an order.
     */
    @Transactional
    public Order removeOrderTaxExemption(Long orderId, BigDecimal taxRate) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        // Recalculate tax
        BigDecimal taxableAmount = order.getSubtotal().subtract(order.getDiscount());
        BigDecimal tax = taxableAmount.multiply(taxRate);

        order.setIsTaxExempt(false);
        order.setTaxExemptionTypeId(null);
        order.setTaxExemptionNumber(null);
        order.setTaxExemptionReason(null);
        order.setTax(tax);

        BigDecimal newTotal = order.getSubtotal()
            .add(order.getDeliveryFee())
            .add(order.getServiceFee())
            .add(order.getEntryFee())
            .add(tax)
            .subtract(order.getDiscount())
            .subtract(order.getBonusUsed());

        order.setTotal(newTotal);
        order.setGrandTotal(newTotal.add(order.getTipAmount()));

        log.info("Tax exemption removed from order {}", orderId);
        return orderRepository.save(order);
    }

    /**
     * Check if a customer is tax exempt and valid.
     */
    public TaxExemptCheckResult checkCustomerExemption(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
            .orElseThrow(() -> new IllegalArgumentException("Customer not found"));

        boolean isExempt = Boolean.TRUE.equals(customer.getIsTaxExempt());
        boolean isExpired = false;

        if (isExempt && customer.getTaxExemptionExpiresAt() != null) {
            isExpired = customer.getTaxExemptionExpiresAt().isBefore(LocalDate.now());
        }

        TaxExemptionType exemptionType = null;
        if (customer.getTaxExemptionTypeId() != null) {
            exemptionType = exemptionTypeRepository.findById(customer.getTaxExemptionTypeId()).orElse(null);
        }

        return TaxExemptCheckResult.builder()
            .customerId(customerId)
            .isTaxExempt(isExempt && !isExpired)
            .exemptionNumber(customer.getTaxExemptionNumber())
            .exemptionTypeName(exemptionType != null ? exemptionType.getName() : null)
            .expiresAt(customer.getTaxExemptionExpiresAt())
            .isExpired(isExpired)
            .build();
    }

    /**
     * Get exemption logs for a restaurant.
     */
    public Page<TaxExemptionLog> getExemptionLogs(Long restaurantId, Pageable pageable) {
        return exemptionLogRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId, pageable);
    }

    /**
     * Get total tax exempted for a date range.
     */
    public BigDecimal getTotalExemptedAmount(Long restaurantId, OffsetDateTime start, OffsetDateTime end) {
        BigDecimal total = exemptionLogRepository.sumExemptedAmountByRestaurantAndDateRange(
            restaurantId, start, end);
        return total != null ? total : BigDecimal.ZERO;
    }
}
