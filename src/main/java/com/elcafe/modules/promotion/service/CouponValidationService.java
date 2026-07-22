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

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponValidationService {

    private final CouponCodeRepository couponCodeRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionUsageRepository promotionUsageRepository;
    private final PromotionProductRepository promotionProductRepository;
    private final OrderRepository orderRepository;
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
     * Calculate the discount amount based on promotion type and order
     */
    public BigDecimal calculateDiscount(Promotion promotion, ValidateCouponRequest request) {
        BigDecimal discount = BigDecimal.ZERO;
        BigDecimal applicableAmount = getApplicableAmount(promotion, request);

        switch (promotion.getPromotionType()) {
            case PERCENTAGE:
                discount = applicableAmount
                        .multiply(promotion.getDiscountValue())
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                break;

            case FIXED_AMOUNT:
                discount = promotion.getDiscountValue();
                // Don't let discount exceed order amount
                if (discount.compareTo(applicableAmount) > 0) {
                    discount = applicableAmount;
                }
                break;

            case FREE_ITEM:
                // Discount is the price of the free product
                if (promotion.getFreeProduct() != null) {
                    discount = promotion.getFreeProduct().getPrice();
                }
                break;

            case BUY_X_GET_Y:
                // Calculate based on qualifying items
                discount = calculateBuyXGetYDiscount(promotion, request);
                break;
        }

        // Apply max discount cap if set
        if (promotion.getRule() != null && promotion.getRule().getMaxDiscountAmount() != null) {
            if (discount.compareTo(promotion.getRule().getMaxDiscountAmount()) > 0) {
                discount = promotion.getRule().getMaxDiscountAmount();
            }
        }

        return discount;
    }

    /**
     * Get the amount that the promotion applies to based on scope
     */
    private BigDecimal getApplicableAmount(Promotion promotion, ValidateCouponRequest request) {
        if (promotion.getPromotionScope() == PromotionScope.ALL) {
            return request.getOrderSubtotal();
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            return request.getOrderSubtotal();
        }

        BigDecimal applicableAmount = BigDecimal.ZERO;

        for (ValidateCouponRequest.OrderItemInfo item : request.getItems()) {
            boolean applies = false;

            if (promotion.getPromotionScope() == PromotionScope.PRODUCT) {
                // Check if product is included
                List<PromotionProduct> included = promotionProductRepository
                        .findIncludedByPromotionAndProduct(promotion.getId(), item.getProductId());
                applies = !included.isEmpty();

                // Check if excluded
                if (applies && promotionProductRepository.isProductExcluded(promotion.getId(), item.getProductId())) {
                    applies = false;
                }
            } else if (promotion.getPromotionScope() == PromotionScope.CATEGORY && item.getCategoryId() != null) {
                // Check if category is included
                List<PromotionProduct> included = promotionProductRepository
                        .findIncludedByPromotionAndCategory(promotion.getId(), item.getCategoryId());
                applies = !included.isEmpty();
            }

            if (applies) {
                applicableAmount = applicableAmount.add(
                        item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
            }
        }

        return applicableAmount;
    }

    /**
     * Calculate discount for BUY_X_GET_Y promotions
     */
    private BigDecimal calculateBuyXGetYDiscount(Promotion promotion, ValidateCouponRequest request) {
        if (promotion.getBuyQuantity() == null || promotion.getGetQuantity() == null ||
                request.getItems() == null || request.getItems().isEmpty()) {
            return BigDecimal.ZERO;
        }

        int totalQualifyingItems = 0;
        BigDecimal lowestPrice = null;

        for (ValidateCouponRequest.OrderItemInfo item : request.getItems()) {
            boolean qualifies = false;

            if (promotion.getPromotionScope() == PromotionScope.ALL) {
                qualifies = true;
            } else if (promotion.getPromotionScope() == PromotionScope.PRODUCT) {
                List<PromotionProduct> included = promotionProductRepository
                        .findIncludedByPromotionAndProduct(promotion.getId(), item.getProductId());
                qualifies = !included.isEmpty();
            } else if (promotion.getPromotionScope() == PromotionScope.CATEGORY && item.getCategoryId() != null) {
                List<PromotionProduct> included = promotionProductRepository
                        .findIncludedByPromotionAndCategory(promotion.getId(), item.getCategoryId());
                qualifies = !included.isEmpty();
            }

            if (qualifies) {
                totalQualifyingItems += item.getQuantity();
                if (lowestPrice == null || item.getPrice().compareTo(lowestPrice) < 0) {
                    lowestPrice = item.getPrice();
                }
            }
        }

        if (lowestPrice == null) {
            return BigDecimal.ZERO;
        }

        // Calculate how many "sets" of buy X get Y
        int buyQty = promotion.getBuyQuantity();
        int getQty = promotion.getGetQuantity();
        int setsRequired = buyQty + getQty;
        int completeSets = totalQualifyingItems / setsRequired;

        // Discount is the price of free items (get quantity * number of sets)
        BigDecimal discountPercent = promotion.getDiscountValue(); // Could be 100% for free, or partial
        return lowestPrice
                .multiply(BigDecimal.valueOf(completeSets * getQty))
                .multiply(discountPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
