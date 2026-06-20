package com.elcafe.modules.promotion.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.promotion.dto.*;
import com.elcafe.modules.promotion.enums.PromotionScope;
import com.elcafe.modules.promotion.enums.PromotionType;
import com.elcafe.modules.promotion.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PromotionControllerTest {
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    @Mock private PromotionService promotionService;
    @Mock private CouponService couponService;
    @Mock private CouponValidationService couponValidationService;
    @Mock private PromotionAnalyticsService promotionAnalyticsService;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @InjectMocks private PromotionController controller;

    private PromotionResponse promoResp;
    private CouponCodeResponse couponResp;

    @BeforeEach void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        promoResp = PromotionResponse.builder().id(1L).name("Summer Sale").active(true)
                .promotionType(PromotionType.PERCENTAGE).discountValue(new BigDecimal("20")).build();
        couponResp = CouponCodeResponse.builder().id(1L).code("SAVE20").promotionId(1L)
                .promotionName("Summer Sale").active(true).build();
    }

    // === Promotion endpoints (7) ===
    @Test @DisplayName("POST /restaurants/{id}/promotions") void createPromo() throws Exception {
        CreatePromotionRequest req = new CreatePromotionRequest();
        req.setName("New"); req.setPromotionType(PromotionType.PERCENTAGE);
        req.setPromotionScope(PromotionScope.ALL); req.setDiscountValue(new BigDecimal("15"));
        req.setStartDate(java.time.LocalDateTime.now().minusDays(1));
        when(promotionService.createPromotion(eq(1L), any())).thenReturn(promoResp);
        mockMvc.perform(post("/api/v1/restaurants/1/promotions").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());
    }
    @Test @DisplayName("GET /restaurants/{id}/promotions") void listPromos() throws Exception {
        when(promotionService.getPromotions(eq(1L), any())).thenReturn(new PageImpl<>(List.of(promoResp), PageRequest.of(0, 20), 1));
        mockMvc.perform(get("/api/v1/restaurants/1/promotions")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /restaurants/{id}/promotions/active") void activePromos() throws Exception {
        when(promotionService.getActivePromotions(1L)).thenReturn(List.of(promoResp));
        mockMvc.perform(get("/api/v1/restaurants/1/promotions/active")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /promotions/{id}") void getPromo() throws Exception {
        when(promotionService.getPromotion(1L)).thenReturn(promoResp);
        mockMvc.perform(get("/api/v1/promotions/1")).andExpect(status().isOk());
    }
    @Test @DisplayName("PUT /promotions/{id}") void updatePromo() throws Exception {
        UpdatePromotionRequest req = new UpdatePromotionRequest(); req.setName("Updated");
        when(promotionService.updatePromotion(eq(1L), any())).thenReturn(promoResp);
        mockMvc.perform(put("/api/v1/promotions/1").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /promotions/{id}/toggle") void togglePromo() throws Exception {
        when(promotionService.togglePromotion(1L)).thenReturn(promoResp);
        mockMvc.perform(post("/api/v1/promotions/1/toggle")).andExpect(status().isOk());
    }
    @Test @DisplayName("DELETE /promotions/{id}") void deletePromo() throws Exception {
        mockMvc.perform(delete("/api/v1/promotions/1")).andExpect(status().isNoContent());
        verify(promotionService).deletePromotion(1L);
    }

    // === Coupon endpoints (11) ===
    @Test @DisplayName("POST /coupons") void createCoupon() throws Exception {
        CouponCodeRequest req = new CouponCodeRequest(); req.setCode("NEW10"); req.setPromotionId(1L);
        when(couponService.createCoupon(any())).thenReturn(couponResp);
        mockMvc.perform(post("/api/v1/coupons").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());
    }
    @Test @DisplayName("POST /coupons/generate-batch") void generateBatch() throws Exception {
        GenerateCouponsRequest req = new GenerateCouponsRequest(); req.setPromotionId(1L); req.setCount(5); req.setCodeLength(8);
        when(couponService.generateCoupons(any())).thenReturn(List.of(couponResp));
        mockMvc.perform(post("/api/v1/coupons/generate-batch").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isCreated());
    }
    @Test @DisplayName("POST /coupons/validate") void validate() throws Exception {
        ValidateCouponRequest req = ValidateCouponRequest.builder().code("SAVE20").restaurantId(1L).orderSubtotal(new BigDecimal("100000")).build();
        when(couponValidationService.validateCoupon(any())).thenReturn(ValidateCouponResponse.invalid("test"));
        mockMvc.perform(post("/api/v1/coupons/validate").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /coupons/{id}") void getCoupon() throws Exception {
        when(couponService.getCoupon(1L)).thenReturn(couponResp);
        mockMvc.perform(get("/api/v1/coupons/1")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /coupons/by-code/{code}") void getByCode() throws Exception {
        when(couponService.getCouponByCode("SAVE20")).thenReturn(couponResp);
        mockMvc.perform(get("/api/v1/coupons/by-code/SAVE20")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /promotions/{id}/coupons") void couponsByPromo() throws Exception {
        when(couponService.getCouponsByPromotion(eq(1L), any())).thenReturn(new PageImpl<>(List.of(couponResp), PageRequest.of(0, 20), 1));
        mockMvc.perform(get("/api/v1/promotions/1/coupons")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /restaurants/{id}/coupons") void couponsByRestaurant() throws Exception {
        when(couponService.getCouponsByRestaurant(eq(1L), any())).thenReturn(new PageImpl<>(List.of(couponResp), PageRequest.of(0, 20), 1));
        mockMvc.perform(get("/api/v1/restaurants/1/coupons")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /customers/{id}/coupons") void customerCoupons() throws Exception {
        when(couponService.getCustomerCoupons(1L)).thenReturn(List.of(couponResp));
        mockMvc.perform(get("/api/v1/customers/1/coupons")).andExpect(status().isOk());
    }
    @Test @DisplayName("PUT /coupons/{id}") void updateCoupon() throws Exception {
        CouponCodeRequest req = new CouponCodeRequest(); req.setMaxUses(200);
        when(couponService.updateCoupon(eq(1L), any())).thenReturn(couponResp);
        mockMvc.perform(put("/api/v1/coupons/1").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req))).andExpect(status().isOk());
    }
    @Test @DisplayName("POST /coupons/{id}/toggle") void toggleCoupon() throws Exception {
        when(couponService.toggleCoupon(1L)).thenReturn(couponResp);
        mockMvc.perform(post("/api/v1/coupons/1/toggle")).andExpect(status().isOk());
    }
    @Test @DisplayName("DELETE /coupons/{id}") void deleteCoupon() throws Exception {
        mockMvc.perform(delete("/api/v1/coupons/1")).andExpect(status().isNoContent());
        verify(couponService).deleteCoupon(1L);
    }

    // === Analytics endpoints (5) ===
    @Test @DisplayName("GET /restaurants/{id}/promotions/analytics") void analytics() throws Exception {
        when(promotionAnalyticsService.getDiscountAnalytics(eq(1L), any(), any()))
                .thenReturn(PromotionAnalyticsService.DiscountAnalytics.builder().restaurantId(1L).build());
        mockMvc.perform(get("/api/v1/restaurants/1/promotions/analytics")
                .param("startDate", "2026-01-01").param("endDate", "2026-03-31")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /promotions/{id}/analytics") void promoPerformance() throws Exception {
        when(promotionAnalyticsService.getPromotionPerformance(1L))
                .thenReturn(PromotionAnalyticsService.PromotionPerformance.builder().promotionId(1L).promotionName("Sale").build());
        mockMvc.perform(get("/api/v1/promotions/1/analytics")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /restaurants/{id}/promotions/analytics/all") void allPerformance() throws Exception {
        when(promotionAnalyticsService.getAllPromotionsPerformance(1L)).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/restaurants/1/promotions/analytics/all")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /restaurants/{id}/promotions/analytics/trends") void trends() throws Exception {
        when(promotionAnalyticsService.getDiscountTrends(eq(1L), any(), any())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/restaurants/1/promotions/analytics/trends")
                .param("startDate", "2026-01-01").param("endDate", "2026-03-31")).andExpect(status().isOk());
    }
    @Test @DisplayName("GET /restaurants/{id}/promotions/analytics/top-coupons") void topCoupons() throws Exception {
        when(promotionAnalyticsService.getTopCoupons(eq(1L), any(), any(), anyInt())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/restaurants/1/promotions/analytics/top-coupons")
                .param("startDate", "2026-01-01").param("endDate", "2026-03-31")).andExpect(status().isOk());
    }
}
