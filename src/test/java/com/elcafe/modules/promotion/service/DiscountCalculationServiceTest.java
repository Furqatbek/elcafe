package com.elcafe.modules.promotion.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.promotion.dto.ApplyDiscountRequest;
import com.elcafe.modules.promotion.dto.HappyHourResponse;
import com.elcafe.modules.promotion.dto.ValidateCouponResponse;
import com.elcafe.modules.promotion.entity.CouponCode;
import com.elcafe.modules.promotion.entity.Promotion;
import com.elcafe.modules.promotion.enums.DiscountType;
import com.elcafe.modules.promotion.repository.CouponCodeRepository;
import com.elcafe.modules.promotion.repository.PromotionRepository;
import com.elcafe.modules.promotion.repository.PromotionUsageRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DiscountCalculationServiceTest {

    @Mock private CouponValidationService couponValidationService;
    @Mock private CouponCodeRepository couponCodeRepository;
    @Mock private PromotionRepository promotionRepository;
    @Mock private PromotionUsageRepository promotionUsageRepository;
    @Mock private HappyHourService happyHourService;
    @InjectMocks private DiscountCalculationService discountCalculationService;

    private Order order;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant(); restaurant.setId(1L); restaurant.setName("Test");
        order = new Order();
        order.setId(1L); order.setRestaurant(restaurant);
        order.setSubtotal(new BigDecimal("100000"));
        order.setTax(new BigDecimal("12000"));
        order.setDeliveryFee(BigDecimal.ZERO);
        order.setServiceFee(BigDecimal.ZERO);
        order.setEntryFee(BigDecimal.ZERO);
        order.setDiscount(BigDecimal.ZERO);
        order.setBonusUsed(BigDecimal.ZERO);
        order.setTipAmount(BigDecimal.ZERO);
        order.setTotal(new BigDecimal("112000"));

        OrderItem item = OrderItem.builder().productId(1L).quantity(2)
                .unitPrice(new BigDecimal("50000")).totalPrice(new BigDecimal("100000")).build();
        order.setItems(new ArrayList<>(List.of(item)));
    }

    @Test @DisplayName("applyDiscount — coupon success")
    void applyDiscount_coupon_success() {
        ApplyDiscountRequest req = ApplyDiscountRequest.builder()
                .discountType(DiscountType.COUPON).couponCode("SAVE20").build();

        Promotion promo = Promotion.builder().id(1L).restaurant(restaurant).build();
        CouponCode coupon = CouponCode.builder().id(1L).code("SAVE20").promotion(promo)
                .usedCount(0).active(true).build();

        when(couponValidationService.validateCoupon(any()))
                .thenReturn(ValidateCouponResponse.valid("SAVE20", 1L, "Summer Sale",
                        com.elcafe.modules.promotion.enums.PromotionType.PERCENTAGE, new BigDecimal("20"), new BigDecimal("20000")));
        when(couponCodeRepository.findByCodeIgnoreCase("SAVE20")).thenReturn(Optional.of(coupon));
        when(couponCodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(promotionUsageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        BigDecimal discount = discountCalculationService.applyDiscount(order, req);

        assertThat(discount).isEqualByComparingTo("20000");
        assertThat(order.getDiscountType()).isEqualTo("COUPON");
    }

    @Test @DisplayName("applyDiscount — happy hour success")
    void applyDiscount_happyHour_success() {
        ApplyDiscountRequest req = ApplyDiscountRequest.builder()
                .discountType(DiscountType.HAPPY_HOUR).happyHourId(1L).build();

        HappyHourResponse happyHourResponse = HappyHourResponse.builder()
                .id(1L).name("Evening").currentlyActive(true).build();
        when(happyHourService.getHappyHour(1L)).thenReturn(happyHourResponse);
        when(happyHourService.calculateHappyHourDiscount(order)).thenReturn(new BigDecimal("15000"));

        BigDecimal discount = discountCalculationService.applyDiscount(order, req);

        assertThat(discount).isEqualByComparingTo("15000");
        assertThat(order.getDiscountType()).isEqualTo("HAPPY_HOUR");
    }

    @Test @DisplayName("applyDiscount — invalid coupon throws")
    void applyDiscount_invalidCoupon_throws() {
        ApplyDiscountRequest req = ApplyDiscountRequest.builder()
                .discountType(DiscountType.COUPON).couponCode("INVALID").build();

        when(couponValidationService.validateCoupon(any()))
                .thenReturn(ValidateCouponResponse.invalid("Invalid coupon"));

        assertThatThrownBy(() -> discountCalculationService.applyDiscount(order, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid coupon");
    }

    @Test @DisplayName("applyDiscount — records promotion usage")
    void applyDiscount_recordsUsage() {
        ApplyDiscountRequest req = ApplyDiscountRequest.builder()
                .discountType(DiscountType.COUPON).couponCode("SAVE20").build();

        Promotion promo = Promotion.builder().id(1L).restaurant(restaurant).build();
        CouponCode coupon = CouponCode.builder().id(1L).code("SAVE20").promotion(promo)
                .usedCount(0).active(true).build();

        when(couponValidationService.validateCoupon(any()))
                .thenReturn(ValidateCouponResponse.valid("SAVE20", 1L, "Sale",
                        com.elcafe.modules.promotion.enums.PromotionType.PERCENTAGE, new BigDecimal("20"), new BigDecimal("20000")));
        when(couponCodeRepository.findByCodeIgnoreCase("SAVE20")).thenReturn(Optional.of(coupon));
        when(couponCodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(promotionUsageRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        discountCalculationService.applyDiscount(order, req);

        verify(promotionUsageRepository).save(any());
        assertThat(coupon.getUsedCount()).isEqualTo(1);
    }

    @Test @DisplayName("removeDiscount — clears discount from order")
    void removeDiscount_success() {
        order.setDiscount(new BigDecimal("20000"));
        order.setDiscountType("COUPON");
        order.setCouponCode("SAVE20");

        discountCalculationService.removeDiscount(order);

        assertThat(order.getDiscount()).isEqualByComparingTo("0");
        assertThat(order.getDiscountType()).isNull();
        assertThat(order.getCouponCode()).isNull();
    }
}
