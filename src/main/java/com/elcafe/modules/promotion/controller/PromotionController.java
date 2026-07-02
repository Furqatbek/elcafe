package com.elcafe.modules.promotion.controller;

import com.elcafe.modules.promotion.dto.*;
import com.elcafe.modules.promotion.service.CouponService;
import com.elcafe.modules.promotion.service.CouponValidationService;
import com.elcafe.modules.promotion.service.PromotionAnalyticsService;
import com.elcafe.modules.promotion.service.PromotionService;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;
    private final CouponService couponService;
    private final CouponValidationService couponValidationService;
    private final PromotionAnalyticsService promotionAnalyticsService;
    private final RestaurantAuthorizationService restaurantAuthorizationService;

    // ==================== PROMOTION ENDPOINTS ====================

    @PostMapping("/restaurants/{restaurantId}/promotions")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PromotionResponse> createPromotion(
            @PathVariable Long restaurantId,
            @Valid @RequestBody CreatePromotionRequest request) {
        log.info("Creating promotion for restaurant {}", restaurantId);
        restaurantAuthorizationService.checkAccess(restaurantId);
        PromotionResponse response = promotionService.createPromotion(restaurantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/restaurants/{restaurantId}/promotions")
    public ResponseEntity<Page<PromotionResponse>> getPromotions(
            @PathVariable Long restaurantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        Pageable pageable = PageRequest.of(page, size);
        Page<PromotionResponse> promotions = promotionService.getPromotions(restaurantId, pageable);
        return ResponseEntity.ok(promotions);
    }

    @GetMapping("/restaurants/{restaurantId}/promotions/active")
    public ResponseEntity<List<PromotionResponse>> getActivePromotions(@PathVariable Long restaurantId) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        List<PromotionResponse> promotions = promotionService.getActivePromotions(restaurantId);
        return ResponseEntity.ok(promotions);
    }

    @PostMapping("/restaurants/{restaurantId}/promotions/check-cart")
    public ResponseEntity<List<CartDiscountResult>> checkCart(
            @PathVariable Long restaurantId,
            @RequestBody CheckCartRequest request) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        return ResponseEntity.ok(promotionService.checkCartPromotions(restaurantId, request.items()));
    }

    public record CheckCartRequest(List<CartItem> items) {}
    public record CartItem(Long productId, int quantity, java.math.BigDecimal unitPrice) {}
    public record CartDiscountResult(Long promotionId, String promotionName, String type,
                                      java.math.BigDecimal discountAmount, String description) {}

    @GetMapping("/promotions/{promotionId}")
    public ResponseEntity<PromotionResponse> getPromotion(@PathVariable Long promotionId) {
        PromotionResponse response = promotionService.getPromotion(promotionId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/promotions/{promotionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PromotionResponse> updatePromotion(
            @PathVariable Long promotionId,
            @RequestBody UpdatePromotionRequest request) {
        log.info("Updating promotion {}", promotionId);
        PromotionResponse response = promotionService.updatePromotion(promotionId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/promotions/{promotionId}/toggle")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PromotionResponse> togglePromotion(@PathVariable Long promotionId) {
        log.info("Toggling promotion {}", promotionId);
        PromotionResponse response = promotionService.togglePromotion(promotionId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/promotions/{promotionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deletePromotion(@PathVariable Long promotionId) {
        log.info("Deleting promotion {}", promotionId);
        promotionService.deletePromotion(promotionId);
        return ResponseEntity.noContent().build();
    }

    // ==================== COUPON ENDPOINTS ====================

    @PostMapping("/coupons")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CouponCodeResponse> createCoupon(@Valid @RequestBody CouponCodeRequest request) {
        log.info("Creating coupon code");
        CouponCodeResponse response = couponService.createCoupon(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/coupons/generate-batch")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<CouponCodeResponse>> generateCoupons(@Valid @RequestBody GenerateCouponsRequest request) {
        log.info("Generating {} coupons", request.getCount());
        List<CouponCodeResponse> coupons = couponService.generateCoupons(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(coupons);
    }

    @PostMapping("/coupons/validate")
    public ResponseEntity<ValidateCouponResponse> validateCoupon(@Valid @RequestBody ValidateCouponRequest request) {
        log.info("Validating coupon: {}", request.getCode());
        ValidateCouponResponse response = couponValidationService.validateCoupon(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/coupons/{couponId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CouponCodeResponse> getCoupon(@PathVariable Long couponId) {
        CouponCodeResponse response = couponService.getCoupon(couponId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/coupons/by-code/{code}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CouponCodeResponse> getCouponByCode(@PathVariable String code) {
        CouponCodeResponse response = couponService.getCouponByCode(code);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/promotions/{promotionId}/coupons")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Page<CouponCodeResponse>> getCouponsByPromotion(
            @PathVariable Long promotionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<CouponCodeResponse> coupons = couponService.getCouponsByPromotion(promotionId, pageable);
        return ResponseEntity.ok(coupons);
    }

    @GetMapping("/restaurants/{restaurantId}/coupons")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Page<CouponCodeResponse>> getCouponsByRestaurant(
            @PathVariable Long restaurantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        restaurantAuthorizationService.checkAccess(restaurantId);
        Pageable pageable = PageRequest.of(page, size);
        Page<CouponCodeResponse> coupons = couponService.getCouponsByRestaurant(restaurantId, pageable);
        return ResponseEntity.ok(coupons);
    }

    @GetMapping("/customers/{customerId}/coupons")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<CouponCodeResponse>> getCustomerCoupons(@PathVariable Long customerId) {
        List<CouponCodeResponse> coupons = couponService.getCustomerCoupons(customerId);
        return ResponseEntity.ok(coupons);
    }

    @PutMapping("/coupons/{couponId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CouponCodeResponse> updateCoupon(
            @PathVariable Long couponId,
            @RequestBody CouponCodeRequest request) {
        log.info("Updating coupon {}", couponId);
        CouponCodeResponse response = couponService.updateCoupon(couponId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/coupons/{couponId}/toggle")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<CouponCodeResponse> toggleCoupon(@PathVariable Long couponId) {
        log.info("Toggling coupon {}", couponId);
        CouponCodeResponse response = couponService.toggleCoupon(couponId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/coupons/{couponId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<Void> deleteCoupon(@PathVariable Long couponId) {
        log.info("Deleting coupon {}", couponId);
        couponService.deleteCoupon(couponId);
        return ResponseEntity.noContent().build();
    }

    // ==================== ANALYTICS ENDPOINTS ====================

    @GetMapping("/restaurants/{restaurantId}/promotions/analytics")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PromotionAnalyticsService.DiscountAnalytics> getDiscountAnalytics(
            @PathVariable Long restaurantId,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        log.info("Getting discount analytics for restaurant {} from {} to {}", restaurantId, startDate, endDate);
        restaurantAuthorizationService.checkAccess(restaurantId);
        PromotionAnalyticsService.DiscountAnalytics analytics = promotionAnalyticsService.getDiscountAnalytics(restaurantId, startDate, endDate);
        return ResponseEntity.ok(analytics);
    }

    @GetMapping("/promotions/{promotionId}/analytics")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<PromotionAnalyticsService.PromotionPerformance> getPromotionPerformance(
            @PathVariable Long promotionId) {
        log.info("Getting performance for promotion {}", promotionId);
        PromotionAnalyticsService.PromotionPerformance performance = promotionAnalyticsService.getPromotionPerformance(promotionId);
        return ResponseEntity.ok(performance);
    }

    @GetMapping("/restaurants/{restaurantId}/promotions/analytics/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<PromotionAnalyticsService.PromotionPerformance>> getAllPromotionsPerformance(
            @PathVariable Long restaurantId) {
        log.info("Getting all promotions performance for restaurant {}", restaurantId);
        restaurantAuthorizationService.checkAccess(restaurantId);
        List<PromotionAnalyticsService.PromotionPerformance> performances = promotionAnalyticsService.getAllPromotionsPerformance(restaurantId);
        return ResponseEntity.ok(performances);
    }

    @GetMapping("/restaurants/{restaurantId}/promotions/analytics/trends")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<PromotionAnalyticsService.DailyDiscountTrend>> getDiscountTrends(
            @PathVariable Long restaurantId,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        log.info("Getting discount trends for restaurant {} from {} to {}", restaurantId, startDate, endDate);
        restaurantAuthorizationService.checkAccess(restaurantId);
        List<PromotionAnalyticsService.DailyDiscountTrend> trends = promotionAnalyticsService.getDiscountTrends(restaurantId, startDate, endDate);
        return ResponseEntity.ok(trends);
    }

    @GetMapping("/restaurants/{restaurantId}/promotions/analytics/top-coupons")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ResponseEntity<List<PromotionAnalyticsService.CouponPerformance>> getTopCoupons(
            @PathVariable Long restaurantId,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
            @RequestParam(defaultValue = "10") int limit) {
        log.info("Getting top coupons for restaurant {} from {} to {}", restaurantId, startDate, endDate);
        restaurantAuthorizationService.checkAccess(restaurantId);
        List<PromotionAnalyticsService.CouponPerformance> topCoupons = promotionAnalyticsService.getTopCoupons(restaurantId, startDate, endDate, limit);
        return ResponseEntity.ok(topCoupons);
    }
}
