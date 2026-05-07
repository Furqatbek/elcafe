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

    @Transactional(readOnly = true)
    public List<com.elcafe.modules.promotion.controller.PromotionController.CartDiscountResult> checkCartPromotions(
            Long restaurantId, List<com.elcafe.modules.promotion.controller.PromotionController.CartItem> items) {
        if (items == null || items.isEmpty()) return Collections.emptyList();

        List<Promotion> activePromotions = promotionRepository.findActivePromotions(restaurantId, java.time.LocalDateTime.now());
        List<com.elcafe.modules.promotion.controller.PromotionController.CartDiscountResult> results = new java.util.ArrayList<>();

        // Load product categories for scope matching
        Map<Long, Long> productCategoryMap = new java.util.HashMap<>();
        for (var item : items) {
            if (item.productId() != null) {
                productRepository.findById(item.productId()).ifPresent(p ->
                        productCategoryMap.put(p.getId(), p.getCategory().getId()));
            }
        }

        for (Promotion promo : activePromotions) {
            if (promo.getPromotionType() == com.elcafe.modules.promotion.enums.PromotionType.BUY_X_GET_Y
                    && promo.getBuyQuantity() != null && promo.getGetQuantity() != null) {

                // Find qualifying items based on scope
                List<com.elcafe.modules.promotion.controller.PromotionController.CartItem> qualifying = new java.util.ArrayList<>();
                Set<Long> applicableCategoryIds = promo.getPromotionProducts().stream()
                        .filter(pp -> pp.getCategory() != null && pp.getInclude())
                        .map(pp -> pp.getCategory().getId())
                        .collect(java.util.stream.Collectors.toSet());
                Set<Long> applicableProductIds = promo.getPromotionProducts().stream()
                        .filter(pp -> pp.getProduct() != null && pp.getInclude())
                        .map(pp -> pp.getProduct().getId())
                        .collect(java.util.stream.Collectors.toSet());

                for (var item : items) {
                    boolean matches = false;
                    if (promo.getPromotionScope() == com.elcafe.modules.promotion.enums.PromotionScope.ALL) {
                        matches = true;
                    } else if (promo.getPromotionScope() == com.elcafe.modules.promotion.enums.PromotionScope.CATEGORY) {
                        Long catId = productCategoryMap.get(item.productId());
                        matches = catId != null && applicableCategoryIds.contains(catId);
                    } else if (promo.getPromotionScope() == com.elcafe.modules.promotion.enums.PromotionScope.PRODUCT) {
                        matches = applicableProductIds.contains(item.productId());
                    }
                    if (matches) {
                        for (int i = 0; i < item.quantity(); i++) {
                            qualifying.add(item);
                        }
                    }
                }

                int totalBuyGetCycle = promo.getBuyQuantity() + promo.getGetQuantity();
                int freeItems = qualifying.size() / totalBuyGetCycle * promo.getGetQuantity();

                if (freeItems > 0) {
                    // Discount = cheapest items * discount percentage
                    List<java.math.BigDecimal> prices = qualifying.stream()
                            .map(com.elcafe.modules.promotion.controller.PromotionController.CartItem::unitPrice)
                            .sorted()
                            .collect(java.util.stream.Collectors.toList());

                    java.math.BigDecimal discountAmount = java.math.BigDecimal.ZERO;
                    for (int i = 0; i < freeItems && i < prices.size(); i++) {
                        discountAmount = discountAmount.add(prices.get(i)
                                .multiply(promo.getDiscountValue())
                                .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP));
                    }

                    results.add(new com.elcafe.modules.promotion.controller.PromotionController.CartDiscountResult(
                            promo.getId(), promo.getName(), "BUY_X_GET_Y", discountAmount,
                            String.format("%d+%d: %d free item(s)", promo.getBuyQuantity(), promo.getGetQuantity(), freeItems)));
                }

            } else if (promo.getPromotionType() == com.elcafe.modules.promotion.enums.PromotionType.PERCENTAGE) {
                java.math.BigDecimal cartTotal = items.stream()
                        .map(i -> i.unitPrice().multiply(java.math.BigDecimal.valueOf(i.quantity())))
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                java.math.BigDecimal discount = cartTotal.multiply(promo.getDiscountValue())
                        .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                if (discount.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    results.add(new com.elcafe.modules.promotion.controller.PromotionController.CartDiscountResult(
                            promo.getId(), promo.getName(), "PERCENTAGE", discount,
                            promo.getDiscountValue() + "% off"));
                }

            } else if (promo.getPromotionType() == com.elcafe.modules.promotion.enums.PromotionType.FIXED_AMOUNT) {
                if (promo.getDiscountValue().compareTo(java.math.BigDecimal.ZERO) > 0) {
                    results.add(new com.elcafe.modules.promotion.controller.PromotionController.CartDiscountResult(
                            promo.getId(), promo.getName(), "FIXED_AMOUNT", promo.getDiscountValue(),
                            promo.getDiscountValue() + " off"));
                }
            }
        }

        return results;
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
