package com.elcafe.modules.promotion.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.promotion.dto.*;
import com.elcafe.modules.promotion.entity.CouponCode;
import com.elcafe.modules.promotion.entity.Promotion;
import com.elcafe.modules.promotion.repository.CouponCodeRepository;
import com.elcafe.modules.promotion.repository.PromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CouponServiceTest {

    @Mock private CouponCodeRepository couponCodeRepository;
    @Mock private PromotionRepository promotionRepository;
    @Mock private CustomerRepository customerRepository;
    @InjectMocks private CouponService couponService;

    private Promotion promotion;
    private CouponCode coupon;

    @BeforeEach
    void setUp() {
        promotion = Promotion.builder().id(1L).name("Summer Sale").build();
        coupon = CouponCode.builder()
                .id(1L).code("SAVE20").promotion(promotion)
                .singleUse(false).maxUses(100).usedCount(0)
                .active(true).build();
    }

    @Test @DisplayName("createCoupon — success")
    void createCoupon_success() {
        CouponCodeRequest req = new CouponCodeRequest();
        req.setCode("NEW10"); req.setPromotionId(1L); req.setActive(true);
        when(couponCodeRepository.existsByCodeIgnoreCase("NEW10")).thenReturn(false);
        when(promotionRepository.findById(1L)).thenReturn(Optional.of(promotion));
        when(couponCodeRepository.save(any())).thenAnswer(i -> { CouponCode c = i.getArgument(0); c.setId(2L); return c; });

        CouponCodeResponse result = couponService.createCoupon(req);

        assertThat(result.getCode()).isEqualTo("NEW10");
        verify(couponCodeRepository).save(any());
    }

    @Test @DisplayName("createCoupon — invalid promotion throws")
    void createCoupon_invalidPromotion_throws() {
        CouponCodeRequest req = new CouponCodeRequest();
        req.setCode("BAD"); req.setPromotionId(99L);
        when(couponCodeRepository.existsByCodeIgnoreCase("BAD")).thenReturn(false);
        when(promotionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.createCoupon(req))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test @DisplayName("generateCoupons — batch generation")
    void generateCoupons_batch() {
        GenerateCouponsRequest req = new GenerateCouponsRequest();
        req.setPromotionId(1L); req.setCount(3); req.setCodeLength(8); req.setPrefix("SUMMER");
        when(promotionRepository.findById(1L)).thenReturn(Optional.of(promotion));
        when(couponCodeRepository.existsByCodeIgnoreCase(anyString())).thenReturn(false);
        when(couponCodeRepository.saveAll(anyList())).thenAnswer(i -> {
            List<CouponCode> list = i.getArgument(0);
            for (int j = 0; j < list.size(); j++) list.get(j).setId((long)(j+1));
            return list;
        });

        List<CouponCodeResponse> result = couponService.generateCoupons(req);

        assertThat(result).hasSize(3);
    }

    @Test @DisplayName("getCoupon — found")
    void getCoupon_found() {
        when(couponCodeRepository.findById(1L)).thenReturn(Optional.of(coupon));
        CouponCodeResponse result = couponService.getCoupon(1L);
        assertThat(result.getCode()).isEqualTo("SAVE20");
    }

    @Test @DisplayName("getCouponByCode — found")
    void getCouponByCode_found() {
        when(couponCodeRepository.findByCodeIgnoreCase("SAVE20")).thenReturn(Optional.of(coupon));
        CouponCodeResponse result = couponService.getCouponByCode("SAVE20");
        assertThat(result.getCode()).isEqualTo("SAVE20");
    }

    @Test @DisplayName("getCouponsByPromotion — paginated")
    void getCouponsByPromotion_returnsList() {
        when(couponCodeRepository.findByPromotion_IdOrderByCreatedAtDesc(1L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(coupon), PageRequest.of(0, 20), 1));
        Page<CouponCodeResponse> result = couponService.getCouponsByPromotion(1L, PageRequest.of(0, 20));
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test @DisplayName("getCustomerCoupons — returns list")
    void getCustomerCoupons_returnsList() {
        when(couponCodeRepository.findActiveByCustomerId(1L)).thenReturn(List.of(coupon));
        List<CouponCodeResponse> result = couponService.getCustomerCoupons(1L);
        assertThat(result).hasSize(1);
    }

    @Test @DisplayName("updateCoupon — success")
    void updateCoupon_success() {
        CouponCodeRequest req = new CouponCodeRequest();
        req.setMaxUses(200); req.setActive(true);
        when(couponCodeRepository.findById(1L)).thenReturn(Optional.of(coupon));
        when(couponCodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CouponCodeResponse result = couponService.updateCoupon(1L, req);
        assertThat(result.getMaxUses()).isEqualTo(200);
    }

    @Test @DisplayName("toggleCoupon — flips active")
    void toggleCoupon_flipsActive() {
        when(couponCodeRepository.findById(1L)).thenReturn(Optional.of(coupon));
        when(couponCodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CouponCodeResponse result = couponService.toggleCoupon(1L);
        assertThat(result.getActive()).isFalse();
    }

    @Test @DisplayName("deleteCoupon — hard deletes unused")
    void deleteCoupon_success() {
        when(couponCodeRepository.findById(1L)).thenReturn(Optional.of(coupon));
        couponService.deleteCoupon(1L);
        verify(couponCodeRepository).delete(coupon);
    }
}
