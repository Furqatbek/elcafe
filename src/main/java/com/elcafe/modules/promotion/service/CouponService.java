package com.elcafe.modules.promotion.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.promotion.dto.*;
import com.elcafe.modules.promotion.entity.CouponCode;
import com.elcafe.modules.promotion.entity.Promotion;
import com.elcafe.modules.promotion.repository.CouponCodeRepository;
import com.elcafe.modules.promotion.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponCodeRepository couponCodeRepository;
    private final PromotionRepository promotionRepository;
    private final CustomerRepository customerRepository;

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public CouponCodeResponse createCoupon(CouponCodeRequest request) {
        log.info("Creating coupon code: {}", request.getCode());

        // Check if code already exists
        if (couponCodeRepository.existsByCodeIgnoreCase(request.getCode())) {
            throw new BadRequestException("Coupon code already exists");
        }

        Promotion promotion = promotionRepository.findById(request.getPromotionId())
                .orElseThrow(() -> new ResourceNotFoundException("Promotion", "id", request.getPromotionId()));

        CouponCode coupon = CouponCode.builder()
                .code(request.getCode().toUpperCase())
                .promotion(promotion)
                .singleUse(request.getSingleUse())
                .maxUses(request.getMaxUses())
                .usedCount(0)
                .validFrom(request.getValidFrom())
                .validUntil(request.getValidUntil())
                .active(request.getActive())
                .build();

        if (request.getAssignedCustomerId() != null) {
            Customer customer = customerRepository.findById(request.getAssignedCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", request.getAssignedCustomerId()));
            coupon.setAssignedCustomer(customer);
        }

        coupon = couponCodeRepository.save(coupon);
        log.info("Coupon created with ID: {}", coupon.getId());

        return mapToResponse(coupon);
    }

    @Transactional
    public List<CouponCodeResponse> generateCoupons(GenerateCouponsRequest request) {
        log.info("Generating {} coupons for promotion {}", request.getCount(), request.getPromotionId());

        Promotion promotion = promotionRepository.findById(request.getPromotionId())
                .orElseThrow(() -> new ResourceNotFoundException("Promotion", "id", request.getPromotionId()));

        List<CouponCode> coupons = new ArrayList<>();
        String prefix = request.getPrefix() != null ? request.getPrefix().toUpperCase() : "";

        for (int i = 0; i < request.getCount(); i++) {
            String code;
            int attempts = 0;
            do {
                code = prefix + generateRandomCode(request.getCodeLength());
                attempts++;
                if (attempts > 100) {
                    throw new BadRequestException("Unable to generate unique coupon codes. Try a longer code length.");
                }
            } while (couponCodeRepository.existsByCodeIgnoreCase(code));

            CouponCode coupon = CouponCode.builder()
                    .code(code)
                    .promotion(promotion)
                    .singleUse(request.getSingleUse())
                    .maxUses(request.getMaxUses())
                    .usedCount(0)
                    .validFrom(request.getValidFrom())
                    .validUntil(request.getValidUntil())
                    .active(true)
                    .build();

            coupons.add(coupon);
        }

        coupons = couponCodeRepository.saveAll(coupons);
        log.info("Generated {} coupons successfully", coupons.size());

        return coupons.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CouponCodeResponse getCoupon(Long couponId) {
        CouponCode coupon = couponCodeRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("CouponCode", "id", couponId));
        return mapToResponse(coupon);
    }

    @Transactional(readOnly = true)
    public CouponCodeResponse getCouponByCode(String code) {
        CouponCode coupon = couponCodeRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ResourceNotFoundException("CouponCode", "code", code));
        return mapToResponse(coupon);
    }

    @Transactional(readOnly = true)
    public Page<CouponCodeResponse> getCouponsByPromotion(Long promotionId, Pageable pageable) {
        return couponCodeRepository.findByPromotion_IdOrderByCreatedAtDesc(promotionId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<CouponCodeResponse> getCouponsByRestaurant(Long restaurantId, Pageable pageable) {
        return couponCodeRepository.findByRestaurantId(restaurantId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<CouponCodeResponse> getCustomerCoupons(Long customerId) {
        return couponCodeRepository.findActiveByCustomerId(customerId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CouponCodeResponse updateCoupon(Long couponId, CouponCodeRequest request) {
        log.info("Updating coupon {}", couponId);

        CouponCode coupon = couponCodeRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("CouponCode", "id", couponId));

        // Update code if changed and not already used
        if (request.getCode() != null && !request.getCode().equalsIgnoreCase(coupon.getCode())) {
            if (coupon.getUsedCount() > 0) {
                throw new BadRequestException("Cannot change code of a coupon that has been used");
            }
            if (couponCodeRepository.existsByCodeIgnoreCase(request.getCode())) {
                throw new BadRequestException("Coupon code already exists");
            }
            coupon.setCode(request.getCode().toUpperCase());
        }

        if (request.getSingleUse() != null) coupon.setSingleUse(request.getSingleUse());
        if (request.getMaxUses() != null) coupon.setMaxUses(request.getMaxUses());
        if (request.getValidFrom() != null) coupon.setValidFrom(request.getValidFrom());
        if (request.getValidUntil() != null) coupon.setValidUntil(request.getValidUntil());
        if (request.getActive() != null) coupon.setActive(request.getActive());

        if (request.getAssignedCustomerId() != null) {
            Customer customer = customerRepository.findById(request.getAssignedCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", request.getAssignedCustomerId()));
            coupon.setAssignedCustomer(customer);
        }

        coupon = couponCodeRepository.save(coupon);
        log.info("Coupon {} updated", couponId);

        return mapToResponse(coupon);
    }

    @Transactional
    public CouponCodeResponse toggleCoupon(Long couponId) {
        CouponCode coupon = couponCodeRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("CouponCode", "id", couponId));

        coupon.setActive(!coupon.getActive());
        coupon = couponCodeRepository.save(coupon);

        log.info("Coupon {} toggled to active={}", couponId, coupon.getActive());
        return mapToResponse(coupon);
    }

    @Transactional
    public void deleteCoupon(Long couponId) {
        CouponCode coupon = couponCodeRepository.findById(couponId)
                .orElseThrow(() -> new ResourceNotFoundException("CouponCode", "id", couponId));

        if (coupon.getUsedCount() > 0) {
            // Soft delete
            coupon.setActive(false);
            couponCodeRepository.save(coupon);
            log.info("Coupon {} deactivated (has been used)", couponId);
        } else {
            couponCodeRepository.delete(coupon);
            log.info("Coupon {} deleted", couponId);
        }
    }

    private String generateRandomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
        }
        return sb.toString();
    }

    private CouponCodeResponse mapToResponse(CouponCode coupon) {
        CouponCodeResponse.CouponCodeResponseBuilder builder = CouponCodeResponse.builder()
                .id(coupon.getId())
                .code(coupon.getCode())
                .promotionId(coupon.getPromotion().getId())
                .promotionName(coupon.getPromotion().getName())
                .singleUse(coupon.getSingleUse())
                .maxUses(coupon.getMaxUses())
                .usedCount(coupon.getUsedCount())
                .validFrom(coupon.getValidFrom())
                .validUntil(coupon.getValidUntil())
                .active(coupon.getActive())
                .currentlyValid(coupon.isValid())
                .createdAt(coupon.getCreatedAt())
                .updatedAt(coupon.getUpdatedAt());

        if (coupon.getAssignedCustomer() != null) {
            builder.assignedCustomerId(coupon.getAssignedCustomer().getId())
                    .assignedCustomerName(coupon.getAssignedCustomer().getFirstName() + " " +
                            coupon.getAssignedCustomer().getLastName());
        }

        return builder.build();
    }
}
