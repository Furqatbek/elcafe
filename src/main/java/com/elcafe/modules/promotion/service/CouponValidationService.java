package com.elcafe.modules.promotion.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.promotion.dto.*;
import com.elcafe.modules.promotion.entity.*;
import com.elcafe.modules.promotion.enums.PromotionScope;
import com.elcafe.modules.promotion.enums.PromotionType;
import com.elcafe.modules.promotion.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponValidationService {

    private final CouponCodeRepository couponCodeRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionUsageRepository promotionUsageRepository;
    private final OrderRepository orderRepository;
    private final PromotionEntitlementService entitlementService;
    private final CustomerRepository customerRepository;

    /**
     * Validate a coupon code and calculate the discount
     */
    @Transactional(readOnly = true)
    public ValidateCouponResponse validateCoupon(ValidateCouponRequest request) {
        log.info("Validating coupon code: {} for restaurant: {}", request.getCode(), request.getRestaurantId());

        // Find coupon
        CouponCode coupon = couponCodeRepository.findByCodeIgnoreCase(request.getCode())
                .orElse(null);

        if (coupon == null) {
            return ValidateCouponResponse.invalid("Invalid coupon code");
        }

        // Check if coupon is active
        if (!coupon.getActive()) {
            return ValidateCouponResponse.invalid("This coupon is no longer active");
        }

        // Check coupon validity dates
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom())) {
            return ValidateCouponResponse.invalid("This coupon is not yet valid");
        }
        if (coupon.getValidUntil() != null && now.isAfter(coupon.getValidUntil())) {
            return ValidateCouponResponse.invalid("This coupon has expired");
        }

        // Check usage limits
        if (coupon.getSingleUse() && coupon.getUsedCount() > 0) {
            return ValidateCouponResponse.invalid("This coupon has already been used");
        }
        if (coupon.getMaxUses() != null && coupon.getUsedCount() >= coupon.getMaxUses()) {
            return ValidateCouponResponse.invalid("This coupon has reached its usage limit");
        }

        // Check if coupon is assigned to specific customer
        if (coupon.getAssignedCustomer() != null && request.getCustomerId() != null) {
            if (!coupon.getAssignedCustomer().getId().equals(request.getCustomerId())) {
                return ValidateCouponResponse.invalid("This coupon is not valid for your account");
            }
        }

        // Get promotion
        Promotion promotion = coupon.getPromotion();

        // Check if promotion is active and valid
        if (!promotion.getActive()) {
            return ValidateCouponResponse.invalid("The associated promotion is no longer active");
        }
        if (!promotion.isValid()) {
            return ValidateCouponResponse.invalid("The associated promotion has expired");
        }

        // Check if promotion belongs to the restaurant
        if (!promotion.getRestaurant().getId().equals(request.getRestaurantId())) {
            return ValidateCouponResponse.invalid("This coupon is not valid at this restaurant");
        }

        // Validate promotion rules
        String ruleError = validatePromotionRules(promotion, request);
        if (ruleError != null) {
            return ValidateCouponResponse.invalid(ruleError);
        }

        // Calculate discount
        BigDecimal calculatedDiscount = calculateDiscount(promotion, request);

        ValidateCouponResponse response = ValidateCouponResponse.valid(
                coupon.getCode(),
                promotion.getId(),
                promotion.getName(),
                promotion.getPromotionType(),
                promotion.getDiscountValue(),
                calculatedDiscount
        );

        // Add free product info if applicable
        if (promotion.getPromotionType() == PromotionType.FREE_ITEM && promotion.getFreeProduct() != null) {
            response.setFreeProductId(promotion.getFreeProduct().getId());
            response.setFreeProductName(promotion.getFreeProduct().getName());
            response.setFreeProductPrice(promotion.getFreeProduct().getPrice());
        }

        // Add BUY_X_GET_Y info if applicable
        if (promotion.getPromotionType() == PromotionType.BUY_X_GET_Y) {
            response.setBuyQuantity(promotion.getBuyQuantity());
            response.setGetQuantity(promotion.getGetQuantity());
        }

        return response;
    }

    /**
     * Validate promotion rules against order details
     */
    private String validatePromotionRules(Promotion promotion, ValidateCouponRequest request) {
        PromotionRule rule = promotion.getRule();
        if (rule == null) {
            return null; // No rules to validate
        }

        // Check minimum order amount
        if (rule.getMinOrderAmount() != null &&
                request.getOrderSubtotal().compareTo(rule.getMinOrderAmount()) < 0) {
            return "Minimum order amount of " + rule.getMinOrderAmount() + " required";
        }

        // Check minimum items
        if (rule.getMinItems() != null && request.getItems() != null) {
            int totalItems = request.getItems().stream()
                    .mapToInt(ValidateCouponRequest.OrderItemInfo::getQuantity)
                    .sum();
            if (totalItems < rule.getMinItems()) {
                return "Minimum " + rule.getMinItems() + " items required";
            }
        }

        // Check applicable order types
        if (rule.getApplicableOrderTypes() != null && !rule.getApplicableOrderTypes().isEmpty() &&
                request.getOrderType() != null) {
            if (!rule.getApplicableOrderTypes().contains(request.getOrderType())) {
                return "This promotion is not valid for " + request.getOrderType() + " orders";
            }
        }

        // Check applicable days
        if (rule.getApplicableDays() != null && !rule.getApplicableDays().isEmpty()) {
            DayOfWeek today = LocalDateTime.now().getDayOfWeek();
            String todayShort = today.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase();
            if (!rule.getApplicableDays().contains(todayShort)) {
                return "This promotion is not valid today";
            }
        }

        // Check time window
        LocalTime now = LocalTime.now();
        if (rule.getStartTime() != null && now.isBefore(rule.getStartTime())) {
            return "This promotion is not yet active today";
        }
        if (rule.getEndTime() != null && now.isAfter(rule.getEndTime())) {
            return "This promotion has ended for today";
        }

        // Check usage limit
        if (rule.getUsageLimit() != null) {
            Long totalUsage = promotionUsageRepository.countByPromotionId(promotion.getId());
            if (totalUsage >= rule.getUsageLimit()) {
                return "This promotion has reached its usage limit";
            }
        }

        // Check per-customer limit
        if (rule.getPerCustomerLimit() != null && request.getCustomerId() != null) {
            Long customerUsage = promotionUsageRepository.countByPromotionIdAndCustomerId(
                    promotion.getId(), request.getCustomerId());
            if (customerUsage >= rule.getPerCustomerLimit()) {
                return "You have already used this promotion the maximum number of times";
            }
        }

        // Check first order only
        if (rule.getFirstOrderOnly() != null && rule.getFirstOrderOnly() && request.getCustomerId() != null) {
            long orderCount = orderRepository.countByCustomer_Id(request.getCustomerId());
            if (orderCount > 0) {
                return "This promotion is only valid for first-time orders";
            }
        }

        return null;
    }

    /**
     * Calculate the discount amount a promotion gives this order.
     *
     * Delegates scope and the per-type arithmetic to PromotionEntitlementService so
     * promoted and unpromoted items can never be pooled. This used to be worked out
     * here from a categoryId supplied by the caller, which meant a client decided
     * which of its items counted toward a category-scoped promotion — and neither
     * caller in this codebase set that field, so category scope silently matched
     * nothing. The product's real category is read server-side now.
     */
    public BigDecimal calculateDiscount(Promotion promotion, ValidateCouponRequest request) {
        if (promotion == null || promotion.getPromotionType() == null) return BigDecimal.ZERO;

        List<PromotionEntitlementService.CartLine> lines = request.getItems() == null
                ? java.util.Collections.emptyList()
                : request.getItems().stream()
                        .map(i -> new PromotionEntitlementService.CartLine(
                                i.getProductId(),
                                i.getQuantity() != null ? i.getQuantity() : 0,
                                i.getPrice() != null ? i.getPrice() : BigDecimal.ZERO))
                        .collect(Collectors.toList());

        boolean scoped = promotion.getPromotionScope() != null
                && promotion.getPromotionScope() != PromotionScope.ALL;

        // An ALL-scope promotion covers the whole order, so the order subtotal is
        // the right base — it can include charges the item list does not enumerate.
        // BUY_X_GET_Y is the exception even then: item counts are its mechanism.
        if (!scoped && promotion.getPromotionType() != PromotionType.BUY_X_GET_Y) {
            return entitlementService.maxDiscountForSubtotal(promotion, request.getOrderSubtotal());
        }

        // A scoped promotion cannot be priced without the items: there is no way to
        // tell what qualifies. This used to fall back to the whole order subtotal,
        // which discounted unpromoted goods outright.
        if (lines.isEmpty()) {
            log.warn("Promotion '{}' (id {}) needs the order items to price {} scope / {} type - "
                            + "none were supplied, so no discount is given",
                    promotion.getName(), promotion.getId(),
                    promotion.getPromotionScope(), promotion.getPromotionType());
            return BigDecimal.ZERO;
        }

        return entitlementService.maxDiscountFor(promotion, lines);
    }
}
