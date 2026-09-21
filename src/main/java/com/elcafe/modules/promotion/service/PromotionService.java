package com.elcafe.modules.promotion.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.promotion.dto.*;
import com.elcafe.modules.promotion.entity.*;
import com.elcafe.modules.promotion.repository.*;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionService {

    private final PromotionRepository promotionRepository;
    private final PromotionRuleRepository promotionRuleRepository;
    private final PromotionProductRepository promotionProductRepository;
    private final PromotionUsageRepository promotionUsageRepository;
    private final CouponCodeRepository couponCodeRepository;
    private final RestaurantRepository restaurantRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public PromotionResponse createPromotion(Long restaurantId, CreatePromotionRequest request) {
        log.info("Creating promotion '{}' for restaurant {}", request.getName(), restaurantId);

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));

        // Check for duplicate name
        if (promotionRepository.existsByRestaurant_IdAndNameIgnoreCase(restaurantId, request.getName())) {
            throw new BadRequestException("A promotion with this name already exists");
        }

        // Create promotion
        Promotion promotion = Promotion.builder()
                .restaurant(restaurant)
                .name(request.getName())
                .description(request.getDescription())
                .promotionType(request.getPromotionType())
                .promotionScope(request.getPromotionScope())
                .discountValue(request.getDiscountValue())
                .buyQuantity(request.getBuyQuantity())
                .getQuantity(request.getGetQuantity())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .active(request.getActive())
                .priority(request.getPriority())
                .stackable(request.getStackable())
                .build();

        // Set free product if applicable
        if (request.getFreeProductId() != null) {
            Product freeProduct = productRepository.findById(request.getFreeProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getFreeProductId()));
            promotion.setFreeProduct(freeProduct);
        }

        promotion = promotionRepository.save(promotion);

        // Create promotion rule if provided
        if (request.getRule() != null) {
            PromotionRule rule = createPromotionRule(promotion, request.getRule());
            promotion.setRule(rule);
        }

        // Create promotion products if provided
        if (request.getPromotionProducts() != null && !request.getPromotionProducts().isEmpty()) {
            for (CreatePromotionRequest.PromotionProductDTO ppDto : request.getPromotionProducts()) {
                PromotionProduct pp = createPromotionProduct(promotion, ppDto);
                promotion.addPromotionProduct(pp);
            }
        }

        promotion = promotionRepository.save(promotion);
        log.info("Promotion created with ID: {}", promotion.getId());

        return mapToResponse(promotion);
    }

    @Transactional
    public PromotionResponse updatePromotion(Long promotionId, UpdatePromotionRequest request) {
        log.info("Updating promotion {}", promotionId);

        Promotion promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion", "id", promotionId));

        // Update fields if provided
        if (request.getName() != null) promotion.setName(request.getName());
        if (request.getDescription() != null) promotion.setDescription(request.getDescription());
        if (request.getPromotionType() != null) promotion.setPromotionType(request.getPromotionType());
        if (request.getPromotionScope() != null) promotion.setPromotionScope(request.getPromotionScope());
        if (request.getDiscountValue() != null) promotion.setDiscountValue(request.getDiscountValue());
        if (request.getBuyQuantity() != null) promotion.setBuyQuantity(request.getBuyQuantity());
        if (request.getGetQuantity() != null) promotion.setGetQuantity(request.getGetQuantity());
        if (request.getStartDate() != null) promotion.setStartDate(request.getStartDate());
        if (request.getEndDate() != null) promotion.setEndDate(request.getEndDate());
        if (request.getActive() != null) promotion.setActive(request.getActive());
        if (request.getPriority() != null) promotion.setPriority(request.getPriority());
        if (request.getStackable() != null) promotion.setStackable(request.getStackable());

        if (request.getFreeProductId() != null) {
            Product freeProduct = productRepository.findById(request.getFreeProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getFreeProductId()));
            promotion.setFreeProduct(freeProduct);
        }

        // Update rule if provided
        if (request.getRule() != null) {
            if (promotion.getRule() != null) {
                updatePromotionRule(promotion.getRule(), request.getRule());
            } else {
                PromotionRule rule = createPromotionRule(promotion, request.getRule());
                promotion.setRule(rule);
            }
        }

        // Update promotion products if provided
        if (request.getPromotionProducts() != null) {
            // Clear existing and add new
            promotion.getPromotionProducts().clear();
            for (CreatePromotionRequest.PromotionProductDTO ppDto : request.getPromotionProducts()) {
                PromotionProduct pp = createPromotionProduct(promotion, ppDto);
                promotion.addPromotionProduct(pp);
            }
        }

        promotion = promotionRepository.save(promotion);
        log.info("Promotion {} updated", promotionId);

        return mapToResponse(promotion);
    }

    @Transactional(readOnly = true)
    public PromotionResponse getPromotion(Long promotionId) {
        Promotion promotion = promotionRepository.findByIdWithDetails(promotionId);
        if (promotion == null) {
            throw new ResourceNotFoundException("Promotion", "id", promotionId);
        }
        return mapToResponse(promotion);
    }

    @Transactional(readOnly = true)
    public Page<PromotionResponse> getPromotions(Long restaurantId, Pageable pageable) {
        return promotionRepository.findByRestaurant_IdOrderByPriorityDescCreatedAtDesc(restaurantId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<PromotionResponse> getActivePromotions(Long restaurantId) {
        return promotionRepository.findActivePromotions(restaurantId, LocalDateTime.now())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PromotionResponse togglePromotion(Long promotionId) {
        Promotion promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion", "id", promotionId));

        promotion.setActive(!promotion.getActive());
        promotion = promotionRepository.save(promotion);

        log.info("Promotion {} toggled to active={}", promotionId, promotion.getActive());
        return mapToResponse(promotion);
    }

    @Transactional
    public void deletePromotion(Long promotionId) {
        Promotion promotion = promotionRepository.findById(promotionId)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion", "id", promotionId));

        // Check if promotion has been used
        Long usageCount = promotionUsageRepository.countByPromotionId(promotionId);
        if (usageCount > 0) {
            // Soft delete by deactivating instead
            promotion.setActive(false);
            promotionRepository.save(promotion);
            log.info("Promotion {} deactivated (has {} usages)", promotionId, usageCount);
        } else {
            promotionRepository.delete(promotion);
            log.info("Promotion {} deleted", promotionId);
        }
    }

    private PromotionRule createPromotionRule(Promotion promotion, CreatePromotionRequest.PromotionRuleDTO ruleDto) {
        PromotionRule rule = PromotionRule.builder()
                .promotion(promotion)
                .minOrderAmount(ruleDto.getMinOrderAmount())
                .maxDiscountAmount(ruleDto.getMaxDiscountAmount())
                .usageLimit(ruleDto.getUsageLimit())
                .perCustomerLimit(ruleDto.getPerCustomerLimit())
                .minItems(ruleDto.getMinItems())
                .applicableOrderTypes(ruleDto.getApplicableOrderTypes() != null ?
                        String.join(",", ruleDto.getApplicableOrderTypes()) : null)
                .applicableDays(ruleDto.getApplicableDays() != null ?
                        String.join(",", ruleDto.getApplicableDays()) : null)
                .startTime(ruleDto.getStartTime())
                .endTime(ruleDto.getEndTime())
                .firstOrderOnly(ruleDto.getFirstOrderOnly())
                .build();
        return promotionRuleRepository.save(rule);
    }

    private void updatePromotionRule(PromotionRule rule, CreatePromotionRequest.PromotionRuleDTO ruleDto) {
        if (ruleDto.getMinOrderAmount() != null) rule.setMinOrderAmount(ruleDto.getMinOrderAmount());
        if (ruleDto.getMaxDiscountAmount() != null) rule.setMaxDiscountAmount(ruleDto.getMaxDiscountAmount());
        if (ruleDto.getUsageLimit() != null) rule.setUsageLimit(ruleDto.getUsageLimit());
        if (ruleDto.getPerCustomerLimit() != null) rule.setPerCustomerLimit(ruleDto.getPerCustomerLimit());
        if (ruleDto.getMinItems() != null) rule.setMinItems(ruleDto.getMinItems());
        if (ruleDto.getApplicableOrderTypes() != null) {
            rule.setApplicableOrderTypes(String.join(",", ruleDto.getApplicableOrderTypes()));
        }
        if (ruleDto.getApplicableDays() != null) {
            rule.setApplicableDays(String.join(",", ruleDto.getApplicableDays()));
        }
        if (ruleDto.getStartTime() != null) rule.setStartTime(ruleDto.getStartTime());
        if (ruleDto.getEndTime() != null) rule.setEndTime(ruleDto.getEndTime());
        if (ruleDto.getFirstOrderOnly() != null) rule.setFirstOrderOnly(ruleDto.getFirstOrderOnly());
        promotionRuleRepository.save(rule);
    }

    private PromotionProduct createPromotionProduct(Promotion promotion, CreatePromotionRequest.PromotionProductDTO ppDto) {
        PromotionProduct pp = PromotionProduct.builder()
                .promotion(promotion)
                .include(ppDto.getInclude())
                .build();

        if (ppDto.getProductId() != null) {
            Product product = productRepository.findById(ppDto.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", ppDto.getProductId()));
            pp.setProduct(product);
        } else if (ppDto.getCategoryId() != null) {
            Category category = categoryRepository.findById(ppDto.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", ppDto.getCategoryId()));
            pp.setCategory(category);
        }

        return promotionProductRepository.save(pp);
    }

    private PromotionResponse mapToResponse(Promotion promotion) {
        PromotionResponse.PromotionResponseBuilder builder = PromotionResponse.builder()
                .id(promotion.getId())
                .restaurantId(promotion.getRestaurant().getId())
                .restaurantName(promotion.getRestaurant().getName())
                .name(promotion.getName())
                .description(promotion.getDescription())
                .promotionType(promotion.getPromotionType())
                .promotionScope(promotion.getPromotionScope())
                .discountValue(promotion.getDiscountValue())
                .buyQuantity(promotion.getBuyQuantity())
                .getQuantity(promotion.getGetQuantity())
                .startDate(promotion.getStartDate())
                .endDate(promotion.getEndDate())
                .active(promotion.getActive())
                .currentlyValid(promotion.isValid())
                .priority(promotion.getPriority())
                .stackable(promotion.getStackable())
                .createdAt(promotion.getCreatedAt())
                .updatedAt(promotion.getUpdatedAt());

        if (promotion.getFreeProduct() != null) {
            builder.freeProductId(promotion.getFreeProduct().getId())
                    .freeProductName(promotion.getFreeProduct().getName());
        }

        // Map rule
        if (promotion.getRule() != null) {
            PromotionRule rule = promotion.getRule();
            builder.rule(PromotionResponse.PromotionRuleResponse.builder()
                    .id(rule.getId())
                    .minOrderAmount(rule.getMinOrderAmount())
                    .maxDiscountAmount(rule.getMaxDiscountAmount())
                    .usageLimit(rule.getUsageLimit())
                    .perCustomerLimit(rule.getPerCustomerLimit())
                    .minItems(rule.getMinItems())
                    .applicableOrderTypes(rule.getApplicableOrderTypes() != null ?
                            Arrays.asList(rule.getApplicableOrderTypes().split(",")) : Collections.emptyList())
                    .applicableDays(rule.getApplicableDays() != null ?
                            Arrays.asList(rule.getApplicableDays().split(",")) : Collections.emptyList())
                    .startTime(rule.getStartTime())
                    .endTime(rule.getEndTime())
                    .firstOrderOnly(rule.getFirstOrderOnly())
                    .build());
        }

        // Map promotion products
        if (promotion.getPromotionProducts() != null) {
            builder.promotionProducts(promotion.getPromotionProducts().stream()
                    .map(pp -> PromotionResponse.PromotionProductResponse.builder()
                            .id(pp.getId())
                            .productId(pp.getProduct() != null ? pp.getProduct().getId() : null)
                            .productName(pp.getProduct() != null ? pp.getProduct().getName() : null)
                            .categoryId(pp.getCategory() != null ? pp.getCategory().getId() : null)
                            .categoryName(pp.getCategory() != null ? pp.getCategory().getName() : null)
                            .include(pp.getInclude())
                            .build())
                    .collect(Collectors.toList()));
        }

        // Add usage statistics
        builder.totalUsage(promotionUsageRepository.countByPromotionId(promotion.getId()));
        builder.totalDiscountGiven(promotionUsageRepository.sumDiscountByPromotionId(promotion.getId()));
        builder.couponCount(couponCodeRepository.countByPromotionId(promotion.getId()));

        return builder.build();
    }
}
