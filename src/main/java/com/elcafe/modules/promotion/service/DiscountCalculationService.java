package com.elcafe.modules.promotion.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.entity.OrderItem;
import com.elcafe.modules.promotion.dto.ApplyDiscountRequest;
import com.elcafe.modules.promotion.dto.ValidateCouponRequest;
import com.elcafe.modules.promotion.dto.ValidateCouponResponse;
import com.elcafe.modules.promotion.entity.CouponCode;
import com.elcafe.modules.promotion.entity.Promotion;
import com.elcafe.modules.promotion.entity.PromotionUsage;
import com.elcafe.modules.promotion.enums.DiscountType;
import com.elcafe.modules.promotion.repository.CouponCodeRepository;
import com.elcafe.modules.promotion.repository.PromotionRepository;
import com.elcafe.modules.promotion.repository.PromotionUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiscountCalculationService {

    private final CouponValidationService couponValidationService;
    private final CouponCodeRepository couponCodeRepository;
    private final PromotionRepository promotionRepository;
    private final PromotionUsageRepository promotionUsageRepository;
    private final HappyHourService happyHourService;

    /**
     * Apply a discount to an order and return the updated discount amount
     */
    @Transactional
    public BigDecimal applyDiscount(Order order, ApplyDiscountRequest request) {
        log.info("Applying discount to order {}: type={}", order.getId(), request.getDiscountType());

        BigDecimal discountAmount = BigDecimal.ZERO;

        switch (request.getDiscountType()) {
            case COUPON:
                discountAmount = applyCouponDiscount(order, request.getCouponCode());
                order.setCouponCode(request.getCouponCode());
                order.setDiscountType(DiscountType.COUPON.name());
                break;

            case PROMOTION:
                discountAmount = applyPromotionDiscount(order, request.getPromotionId());
                order.setDiscountType(DiscountType.PROMOTION.name());
                break;

            case MANUAL:
                discountAmount = applyManualDiscount(order, request);
                order.setDiscountType(DiscountType.MANUAL.name());
                order.setDiscountReason(request.getDiscountReason());
                break;

            case HAPPY_HOUR:
                discountAmount = applyHappyHourDiscount(order, request.getHappyHourId());
                order.setDiscountType(DiscountType.HAPPY_HOUR.name());
                break;
        }

        order.setDiscount(discountAmount);
        recalculateOrderTotal(order);

        log.info("Discount of {} applied to order {}", discountAmount, order.getId());
        return discountAmount;
    }

    /**
     * Apply a coupon code discount
     */
    private BigDecimal applyCouponDiscount(Order order, String couponCode) {
        if (couponCode == null || couponCode.isBlank()) {
            throw new BadRequestException("Coupon code is required");
        }

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

        // Validate coupon
        ValidateCouponResponse response = couponValidationService.validateCoupon(validateRequest);
        if (!response.getValid()) {
            throw new BadRequestException(response.getErrorMessage());
        }

        // Record usage
        CouponCode coupon = couponCodeRepository.findByCodeIgnoreCase(couponCode)
                .orElseThrow(() -> new BadRequestException("Invalid coupon code"));

        recordPromotionUsage(coupon.getPromotion(), coupon, order.getCustomer(), order, response.getCalculatedDiscount());

        // Increment coupon usage
        coupon.incrementUsage();
        couponCodeRepository.save(coupon);

        order.setPromotionId(coupon.getPromotion().getId());
        return response.getCalculatedDiscount();
    }

    /**
     * Apply an automatic promotion discount
     */
    private BigDecimal applyPromotionDiscount(Order order, Long promotionId) {
        if (promotionId == null) {
            throw new BadRequestException("Promotion ID is required");
        }

        Promotion promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new BadRequestException("Promotion not found"));

        if (!promotion.isValid()) {
            throw new BadRequestException("This promotion is no longer valid");
        }

        // Build validation request
        ValidateCouponRequest validateRequest = ValidateCouponRequest.builder()
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

        BigDecimal discount = couponValidationService.calculateDiscount(promotion, validateRequest);

        // Record usage
        recordPromotionUsage(promotion, null, order.getCustomer(), order, discount);

        order.setPromotionId(promotionId);
        return discount;
    }

    /**
     * Apply a manual discount
     */
    private BigDecimal applyManualDiscount(Order order, ApplyDiscountRequest request) {
        BigDecimal discount = BigDecimal.ZERO;

        if (request.getManualDiscountAmount() != null && request.getManualDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            discount = request.getManualDiscountAmount();
        } else if (request.getManualDiscountPercent() != null && request.getManualDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
            discount = order.getSubtotal()
                    .multiply(request.getManualDiscountPercent())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        // Don't allow discount greater than subtotal
        if (discount.compareTo(order.getSubtotal()) > 0) {
            discount = order.getSubtotal();
        }

        return discount;
    }

    /**
     * Apply a happy hour discount
     */
    private BigDecimal applyHappyHourDiscount(Order order, Long happyHourId) {
        // If specific happy hour ID provided, validate it's currently active
        if (happyHourId != null) {
            var happyHour = happyHourService.getHappyHour(happyHourId);
            if (!Boolean.TRUE.equals(happyHour.getCurrentlyActive())) {
                throw new BadRequestException("This happy hour is not currently active");
            }
            order.setHappyHourId(happyHourId);
        }

        // Calculate discount based on order items
        BigDecimal discount = happyHourService.calculateHappyHourDiscount(order);

        if (discount.compareTo(BigDecimal.ZERO) == 0) {
            throw new BadRequestException("No happy hour discount applicable for this order");
        }

        return discount;
    }

    /**
     * Record promotion usage for analytics
     */
    private void recordPromotionUsage(Promotion promotion, CouponCode couponCode, Customer customer,
                                       Order order, BigDecimal discountAmount) {
        PromotionUsage usage = PromotionUsage.builder()
                .promotion(promotion)
                .couponCode(couponCode)
                .customer(customer)
                .order(order)
                .discountAmount(discountAmount)
                .usedAt(LocalDateTime.now())
                .build();

        promotionUsageRepository.save(usage);
        log.info("Promotion usage recorded: promotion={}, order={}, discount={}",
                promotion.getId(), order.getId(), discountAmount);
    }

    /**
     * Remove discount from an order
     */
    @Transactional
    public void removeDiscount(Order order) {
        log.info("Removing discount from order {}", order.getId());

        order.setDiscount(BigDecimal.ZERO);
        order.setPromotionId(null);
        order.setHappyHourId(null);
        order.setCouponCode(null);
        order.setDiscountType(null);
        order.setDiscountReason(null);

        recalculateOrderTotal(order);
    }

    /**
     * Recalculate order total after discount changes
     */
    private void recalculateOrderTotal(Order order) {
        BigDecimal subtotal = order.getSubtotal();
        BigDecimal tax = order.getTax() != null ? order.getTax() : BigDecimal.ZERO;
        BigDecimal deliveryFee = order.getDeliveryFee() != null ? order.getDeliveryFee() : BigDecimal.ZERO;
        BigDecimal serviceFee = order.getServiceFee() != null ? order.getServiceFee() : BigDecimal.ZERO;
        BigDecimal entryFee = order.getEntryFee() != null ? order.getEntryFee() : BigDecimal.ZERO;
        BigDecimal discount = order.getDiscount() != null ? order.getDiscount() : BigDecimal.ZERO;

        BigDecimal total = subtotal
                .add(tax)
                .add(deliveryFee)
                .add(serviceFee)
                .add(entryFee)
                .subtract(discount);

        // Don't let total go negative
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            total = BigDecimal.ZERO;
        }

        order.setTotal(total);

        // Update grand total (total + tip)
        BigDecimal tipAmount = order.getTipAmount() != null ? order.getTipAmount() : BigDecimal.ZERO;
        order.setGrandTotal(total.add(tipAmount));
    }
}
