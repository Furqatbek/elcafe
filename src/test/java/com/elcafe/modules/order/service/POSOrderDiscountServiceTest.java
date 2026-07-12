package com.elcafe.modules.order.service;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.exception.BadRequestException;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.promotion.dto.ApplyDiscountRequest;
import com.elcafe.modules.promotion.dto.ValidateCouponResponse;
import com.elcafe.modules.promotion.service.CouponValidationService;
import com.elcafe.modules.promotion.service.DiscountCalculationService;
import com.elcafe.modules.promotion.service.HappyHourService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.createOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class POSOrderDiscountServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private DiscountCalculationService discountCalculationService;
    @Mock private CouponValidationService couponValidationService;
    @Mock private HappyHourService happyHourService;

    @InjectMocks private POSOrderDiscountService discountService;

    private Order order;

    @BeforeEach
    void setUp() {
        order = createOrder(1L, OrderStatus.PREPARING);
        order.setSubtotal(BigDecimal.valueOf(100000));
        order.setTotal(BigDecimal.valueOf(100000));
    }

    // ==================== applyDiscount ====================

    @Test
    @DisplayName("Apply discount — delegates to DiscountCalculationService")
    void applyDiscount_delegates() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        ApplyDiscountRequest request = new ApplyDiscountRequest();
        discountService.applyDiscount(1L, request);

        verify(discountCalculationService).applyDiscount(order, request);
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("Apply discount — order not found throws")
    void applyDiscount_orderNotFound_throws() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> discountService.applyDiscount(99L, new ApplyDiscountRequest()));
    }

    @Test
    @DisplayName("Apply discount — COMPLETED order throws")
    void applyDiscount_completedOrder_throws() {
        order.setStatus(OrderStatus.COMPLETED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(BadRequestException.class,
                () -> discountService.applyDiscount(1L, new ApplyDiscountRequest()));
    }

    @Test
    @DisplayName("Apply discount — DELIVERED order throws")
    void applyDiscount_deliveredOrder_throws() {
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(BadRequestException.class,
                () -> discountService.applyDiscount(1L, new ApplyDiscountRequest()));
    }

    // ==================== removeDiscount ====================

    @Test
    @DisplayName("Remove discount — delegates to DiscountCalculationService")
    void removeDiscount_delegates() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        discountService.removeDiscount(1L);

        verify(discountCalculationService).removeDiscount(order);
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("Remove discount — order not found throws")
    void removeDiscount_orderNotFound_throws() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> discountService.removeDiscount(99L));
    }

    // ==================== validateCoupon ====================

    @Test
    @DisplayName("Validate coupon — builds request and delegates")
    void validateCoupon_delegatesToService() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        ValidateCouponResponse response = ValidateCouponResponse.builder().valid(true).build();
        when(couponValidationService.validateCoupon(any())).thenReturn(response);

        ValidateCouponResponse result = discountService.validateCoupon(1L, "SAVE10");

        assertNotNull(result);
        verify(couponValidationService).validateCoupon(any());
    }

    // ==================== calculateHappyHourDiscountPreview ====================

    @Test
    @DisplayName("Happy hour preview — delegates to HappyHourService")
    void happyHourPreview_delegates() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(happyHourService.calculateHappyHourDiscount(order)).thenReturn(BigDecimal.valueOf(20000));

        BigDecimal result = discountService.calculateHappyHourDiscountPreview(1L);

        assertEquals(0, BigDecimal.valueOf(20000).compareTo(result));
    }

    @Test
    @DisplayName("Happy hour preview — order not found throws")
    void happyHourPreview_orderNotFound_throws() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> discountService.calculateHappyHourDiscountPreview(99L));
    }
}
