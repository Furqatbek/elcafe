package com.elcafe.modules.promotion.service;

import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.promotion.dto.ValidateCouponRequest;
import com.elcafe.modules.promotion.dto.ValidateCouponResponse;
import com.elcafe.modules.promotion.entity.*;
import com.elcafe.modules.promotion.enums.PromotionScope;
import com.elcafe.modules.promotion.enums.PromotionType;
import com.elcafe.modules.promotion.repository.*;
import com.elcafe.modules.restaurant.entity.Restaurant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CouponValidationServiceTest {

    @Mock private CouponCodeRepository couponCodeRepository;
    @Mock private PromotionRepository promotionRepository;
    @Mock private PromotionUsageRepository promotionUsageRepository;
    @Mock private PromotionProductRepository promotionProductRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private CustomerRepository customerRepository;
    @InjectMocks private CouponValidationService couponValidationService;

    private Restaurant restaurant;
    private Promotion promotion;
    private PromotionRule rule;
    private CouponCode coupon;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant(); restaurant.setId(1L); restaurant.setName("Test");

        rule = PromotionRule.builder().id(1L)
                .minOrderAmount(new BigDecimal("50000")).build();

        promotion = Promotion.builder().id(1L).restaurant(restaurant).name("Sale")
                .promotionType(PromotionType.PERCENTAGE).promotionScope(PromotionScope.ALL)
                .discountValue(new BigDecimal("20")).active(true)
                .startDate(LocalDateTime.now().minusDays(1)).endDate(LocalDateTime.now().plusDays(30))
                .rule(rule).build();
        rule.setPromotion(promotion);

        coupon = CouponCode.builder().id(1L).code("SAVE20").promotion(promotion)
                .singleUse(false).maxUses(100).usedCount(0)
                .validFrom(LocalDateTime.now().minusDays(1)).validUntil(LocalDateTime.now().plusDays(30))
                .active(true).build();
    }

    private ValidateCouponRequest buildRequest(BigDecimal subtotal) {
        return ValidateCouponRequest.builder()
                .code("SAVE20").restaurantId(1L).customerId(1L)
                .orderSubtotal(subtotal).orderType("DINE_IN")
                .items(List.of(ValidateCouponRequest.OrderItemInfo.builder()
                        .productId(1L).quantity(2).price(new BigDecimal("40000")).build()))
                .build();
    }

    @Test @DisplayName("validateCoupon — valid returns discount")
    void validateCoupon_valid() {
        when(couponCodeRepository.findByCodeIgnoreCase("SAVE20")).thenReturn(Optional.of(coupon));
        when(promotionUsageRepository.countByPromotionId(1L)).thenReturn(0L);
        when(promotionUsageRepository.countByPromotionIdAndCustomerId(1L, 1L)).thenReturn(0L);

        ValidateCouponResponse result = couponValidationService.validateCoupon(buildRequest(new BigDecimal("80000")));

        assertThat(result.getValid()).isTrue();
        assertThat(result.getCalculatedDiscount()).isNotNull();
    }

    @Test @DisplayName("validateCoupon — invalid code")
    void validateCoupon_invalidCode() {
        when(couponCodeRepository.findByCodeIgnoreCase("BAD")).thenReturn(Optional.empty());

        ValidateCouponResponse result = couponValidationService.validateCoupon(
                ValidateCouponRequest.builder().code("BAD").restaurantId(1L).build());

        assertThat(result.getValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("Invalid");
    }

    @Test @DisplayName("validateCoupon — expired coupon")
    void validateCoupon_expired() {
        coupon.setValidUntil(LocalDateTime.now().minusDays(1));
        when(couponCodeRepository.findByCodeIgnoreCase("SAVE20")).thenReturn(Optional.of(coupon));

        ValidateCouponResponse result = couponValidationService.validateCoupon(buildRequest(new BigDecimal("80000")));

        assertThat(result.getValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("expired");
    }

    @Test @DisplayName("validateCoupon — usage limit reached")
    void validateCoupon_usageLimitReached() {
        coupon.setMaxUses(5);
        coupon.setUsedCount(5);
        when(couponCodeRepository.findByCodeIgnoreCase("SAVE20")).thenReturn(Optional.of(coupon));

        ValidateCouponResponse result = couponValidationService.validateCoupon(buildRequest(new BigDecimal("80000")));

        assertThat(result.getValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("usage limit");
    }

    @Test @DisplayName("validateCoupon — per-customer limit reached")
    void validateCoupon_customerLimitReached() {
        rule.setPerCustomerLimit(1);
        when(couponCodeRepository.findByCodeIgnoreCase("SAVE20")).thenReturn(Optional.of(coupon));
        when(promotionUsageRepository.countByPromotionIdAndCustomerId(1L, 1L)).thenReturn(1L);

        ValidateCouponResponse result = couponValidationService.validateCoupon(buildRequest(new BigDecimal("80000")));

        assertThat(result.getValid()).isFalse();
    }

    @Test @DisplayName("validateCoupon — minimum order amount not met")
    void validateCoupon_minimumOrderAmount() {
        when(couponCodeRepository.findByCodeIgnoreCase("SAVE20")).thenReturn(Optional.of(coupon));
        when(promotionUsageRepository.countByPromotionId(1L)).thenReturn(0L);
        when(promotionUsageRepository.countByPromotionIdAndCustomerId(1L, 1L)).thenReturn(0L);

        ValidateCouponResponse result = couponValidationService.validateCoupon(buildRequest(new BigDecimal("30000")));

        assertThat(result.getValid()).isFalse();
        assertThat(result.getErrorMessage()).containsIgnoringCase("minimum");
    }

    @Test @DisplayName("validateCoupon — inactive coupon")
    void validateCoupon_inactiveCoupon() {
        coupon.setActive(false);
        when(couponCodeRepository.findByCodeIgnoreCase("SAVE20")).thenReturn(Optional.of(coupon));

        ValidateCouponResponse result = couponValidationService.validateCoupon(buildRequest(new BigDecimal("80000")));

        assertThat(result.getValid()).isFalse();
    }

    @Test @DisplayName("calculateDiscount — percentage type")
    void calculateDiscount_percentage() {
        ValidateCouponRequest req = buildRequest(new BigDecimal("100000"));
        BigDecimal discount = couponValidationService.calculateDiscount(promotion, req);
        assertThat(discount).isEqualByComparingTo("20000"); // 20% of 100000
    }
}
