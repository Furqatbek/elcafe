package com.elcafe.bff.customer.service;

import com.elcafe.bff.customer.dto.CustomerMenuDTO;
import com.elcafe.modules.menu.entity.*;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.promotion.repository.PromotionRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * BFF Assembler for Customer Menu.
 * Transforms internal menu structure to customer-friendly format.
 */
@Service
@RequiredArgsConstructor
public class CustomerMenuAssembler {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final PromotionRepository promotionRepository;

    @Transactional(readOnly = true)
    public CustomerMenuDTO assembleMenu(Restaurant restaurant) {
        Long restaurantId = restaurant.getId();

        return CustomerMenuDTO.builder()
                .restaurant(assembleRestaurantInfo(restaurant))
                .categories(assembleCategories(restaurantId))
                .featured(assembleFeaturedProducts(restaurantId))
                .promotions(assemblePromotions(restaurantId))
                .build();
    }

    private CustomerMenuDTO.RestaurantInfoDTO assembleRestaurantInfo(Restaurant restaurant) {
        return CustomerMenuDTO.RestaurantInfoDTO.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .logoUrl(restaurant.getLogoUrl())
                .coverImageUrl(restaurant.getBannerUrl())
                .description(restaurant.getDescription())
                .cuisineType(null)
                .rating(restaurant.getRating() != null ? restaurant.getRating().doubleValue() : null)
                .reviewCount(0)
                .address(restaurant.getAddress())
                .isOpen(restaurant.getAcceptingOrders())
                .estimatedDeliveryMinutes(restaurant.getEstimatedDeliveryTimeMinutes())
                .minimumOrderAmount(restaurant.getMinimumOrderAmount())
                .deliveryFee(restaurant.getDeliveryFee())
                .build();
    }

    private List<CustomerMenuDTO.MenuCategoryDTO> assembleCategories(Long restaurantId) {
        return categoryRepository.findByRestaurant_IdAndActiveTrueOrderBySortOrder(restaurantId)
                .stream()
                .map(this::mapToMenuCategory)
                .collect(Collectors.toList());
    }

    private CustomerMenuDTO.MenuCategoryDTO mapToMenuCategory(Category category) {
        List<CustomerMenuDTO.MenuProductDTO> products = category.getProducts() != null
                ? category.getProducts().stream()
                    .filter(p -> p.getStatus() == ProductStatus.LIVE)
                    .map(this::mapToMenuProduct)
                    .collect(Collectors.toList())
                : List.of();

        return CustomerMenuDTO.MenuCategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .products(products)
                .build();
    }

    private CustomerMenuDTO.MenuProductDTO mapToMenuProduct(Product product) {
        BigDecimal price = product.getPrice();

        return CustomerMenuDTO.MenuProductDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .imageUrl(product.getImageUrl())
                .price(price)
                .originalPrice(null)
                .isOnSale(false)
                .saleLabel(null)
                .isAvailable(product.getIsAvailable())
                .isPopular(product.getFeatured())
                .isNew(false)
                .dietaryTags(null)
                .allergens(null)
                .calories(null)
                .preparationTime(null)
                .variants(mapVariants(product))
                .addOnGroups(mapAddOnGroups(product))
                .build();
    }

    private List<CustomerMenuDTO.ProductVariantDTO> mapVariants(Product product) {
        if (product.getVariants() == null) {
            return List.of();
        }

        return product.getVariants().stream()
                .filter(v -> v.getIsAvailable() != null && v.getIsAvailable())
                .map(variant -> CustomerMenuDTO.ProductVariantDTO.builder()
                        .id(variant.getId())
                        .name(variant.getName())
                        .price(variant.getPrice())
                        .isAvailable(variant.getIsAvailable())
                        .build())
                .collect(Collectors.toList());
    }

    private List<CustomerMenuDTO.ProductAddOnGroupDTO> mapAddOnGroups(Product product) {
        if (product.getAddOnGroups() == null) {
            return List.of();
        }

        return product.getAddOnGroups().stream()
                .filter(g -> g.getActive() != null && g.getActive())
                .map(group -> CustomerMenuDTO.ProductAddOnGroupDTO.builder()
                        .id(group.getId())
                        .name(group.getName())
                        .description(group.getDescription())
                        .isRequired(group.getRequired())
                        .minSelection(group.getMinSelection())
                        .maxSelection(group.getMaxSelection())
                        .options(mapAddOnOptions(group))
                        .build())
                .collect(Collectors.toList());
    }

    private List<CustomerMenuDTO.ProductAddOnDTO> mapAddOnOptions(AddOnGroup group) {
        if (group.getAddOns() == null) {
            return List.of();
        }

        return group.getAddOns().stream()
                .filter(a -> a.getAvailable() != null && a.getAvailable())
                .map(addOn -> CustomerMenuDTO.ProductAddOnDTO.builder()
                        .id(addOn.getId())
                        .name(addOn.getName())
                        .price(addOn.getPrice())
                        .isAvailable(addOn.getAvailable())
                        .build())
                .collect(Collectors.toList());
    }

    private List<CustomerMenuDTO.FeaturedProductDTO> assembleFeaturedProducts(Long restaurantId) {
        return productRepository.findByRestaurant_IdAndStatus(restaurantId, ProductStatus.LIVE)
                .stream()
                .filter(p -> p.getFeatured() != null && p.getFeatured())
                .limit(6)
                .map(product -> CustomerMenuDTO.FeaturedProductDTO.builder()
                        .productId(product.getId())
                        .name(product.getName())
                        .imageUrl(product.getImageUrl())
                        .price(product.getPrice())
                        .tagline(truncateDescription(product.getDescription()))
                        .build())
                .collect(Collectors.toList());
    }

    private String truncateDescription(String description) {
        if (description == null) {
            return null;
        }
        return description.length() > 50 ? description.substring(0, 47) + "..." : description;
    }

    private List<CustomerMenuDTO.PromotionBannerDTO> assemblePromotions(Long restaurantId) {
        LocalDateTime now = LocalDateTime.now();

        return promotionRepository.findActivePromotions(restaurantId, now)
                .stream()
                .limit(3)
                .map(promotion -> CustomerMenuDTO.PromotionBannerDTO.builder()
                        .id(promotion.getId())
                        .title(promotion.getName())
                        .description(promotion.getDescription())
                        .imageUrl(null)  // Promotion entity doesn't have bannerImageUrl field
                        .promoCode(getFirstCouponCode(promotion))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Get the first active coupon code from a promotion, if available.
     */
    private String getFirstCouponCode(com.elcafe.modules.promotion.entity.Promotion promotion) {
        if (promotion.getCouponCodes() == null || promotion.getCouponCodes().isEmpty()) {
            return null;
        }
        return promotion.getCouponCodes().stream()
                .filter(cc -> cc.getActive() != null && cc.getActive())
                .map(cc -> cc.getCode())
                .findFirst()
                .orElse(null);
    }
}
