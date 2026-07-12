package com.elcafe.modules.order.service;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.promotion.dto.ApplyDiscountRequest;
import com.elcafe.modules.promotion.dto.ValidateCouponRequest;
import com.elcafe.modules.promotion.dto.ValidateCouponResponse;
import com.elcafe.modules.promotion.service.CouponValidationService;
import com.elcafe.modules.promotion.service.DiscountCalculationService;
import com.elcafe.modules.promotion.service.HappyHourService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.stream.Collectors;

/**
 * Service responsible for discount operations on orders.
 * Handles coupon validation, discount application, and happy hour calculations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class POSOrderDiscountService {

    private final OrderRepository orderRepository;
    private final DiscountCalculationService discountCalculationService;
    private final CouponValidationService couponValidationService;
    private final HappyHourService happyHourService;

    /**
     * Apply a discount to an order (coupon, promotion, manual, or happy hour)
     */
    @Transactional
    public Order applyDiscount(Long orderId, ApplyDiscountRequest request) {
        log.info("Applying discount to order {}: type={}", orderId, request.getDiscountType());

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

        validateOrderCanBeModified(order);

        // Apply discount using the discount calculation service
        discountCalculationService.applyDiscount(order, request);

        Order savedOrder = orderRepository.save(order);

        log.info("Discount applied to order {}: discount={}, newTotal={}",
                orderId, savedOrder.getDiscount(), savedOrder.getTotal());

        return savedOrder;
    }

    /**
     * Remove discount from an order
     */
    @Transactional
    public Order removeDiscount(Long orderId) {
        log.info("Removing discount from order {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

        validateOrderCanBeModified(order);

        // Remove discount using the discount calculation service
        discountCalculationService.removeDiscount(order);

        Order savedOrder = orderRepository.save(order);

        log.info("Discount removed from order {}: newTotal={}", orderId, savedOrder.getTotal());

        return savedOrder;
    }

    /**
     * Validate a coupon code for an order without applying it
     */
    @Transactional(readOnly = true)
    public ValidateCouponResponse validateCoupon(Long orderId, String couponCode) {
        log.info("Validating coupon {} for order {}", couponCode, orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

        // Build validation request from order
        ValidateCouponRequest validateRequest = ValidateCouponRequest.builder()
                .code(couponCode)
                .restaurantId(order.getRestaurant().getId())
                .customerId(order.getCustomer() != null ? order.getCustomer().getId() : null)
                .orderSubtotal(order.getSubtotal())
                .orderType(order.getOrderType() != null ? order.getOrderType().name() : null)
                .items(order.getItems().stream()
                        .map(item -> ValidateCouponRequest.OrderItemInfo.builder()
                                .productId(item.getProductId())
                                .quantity(item.getQuantity())
                                .price(item.getUnitPrice())
                                .build())
                        .collect(Collectors.toList()))
                .build();

        return couponValidationService.validateCoupon(validateRequest);
    }

    /**
     * Calculate happy hour discount preview for an order without applying it
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateHappyHourDiscountPreview(Long orderId) {
        log.info("Calculating happy hour discount preview for order {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

        return happyHourService.calculateHappyHourDiscount(order);
    }

    /**
     * Validate that the order can be modified
     */
    private void validateOrderCanBeModified(Order order) {
        if (!canModifyOrder(order)) {
            throw new BadRequestException("Order cannot be modified in status: " + order.getStatus());
        }
    }

    /**
     * Check if order can be modified based on its status
     */
    private boolean canModifyOrder(Order order) {
        return order.getStatus() == OrderStatus.NEW ||
                order.getStatus() == OrderStatus.PENDING ||
                order.getStatus() == OrderStatus.ACCEPTED ||
                order.getStatus() == OrderStatus.PREPARING;
    }
}
