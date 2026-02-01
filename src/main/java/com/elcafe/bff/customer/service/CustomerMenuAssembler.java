package com.elcafe.bff.customer.service;

import com.elcafe.bff.customer.dto.CustomerMenuDTO;
import com.elcafe.modules.menu.entity.*;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.promotion.entity.Promotion;
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
                .coverImageUrl(restaurant.getCoverImageUrl())
                .description(restaurant.getDescription())
                .cuisineType(restaurant.getCuisineType())
                .rating(restaurant.getAverageRating())
                .reviewCount(restaurant.getReviewCount())
                .address(restaurant.getAddress())
                .isOpen(restaurant.getIsOpen())
                .estimatedDeliveryMinutes(restaurant.getEstimatedDeliveryMinutes())
                .minimumOrderAmount(restaurant.getMinimumOrderAmount())
                .deliveryFee(restaurant.getDeliveryFee())
                .build();
    }

    private List<CustomerMenuDTO.MenuCategoryDTO> assembleCategories(Long restaurantId) {
        return categoryRepository.findByRestaurantIdAndIsActiveTrue(restaurantId)
                .stream()
                .map(this::mapToMenuCategory)
                .collect(Collectors.toList());
    }

    private CustomerMenuDTO.MenuCategoryDTO mapToMenuCategory(Category category) {
        List<CustomerMenuDTO.MenuProductDTO> products = category.getProducts() != null
                ? category.getProducts().stream()
                    .filter(Product::getIsActive)
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
        BigDecimal originalPrice = null;
        Boolean isOnSale = false;
        String saleLabel = null;

        // Check for active promotions
        if (product.getDiscountedPrice() != null && product.getDiscountedPrice().compareTo(product.getPrice()) < 0) {
            originalPrice = product.getPrice();
            isOnSale = true;
            saleLabel = "Sale";
        }

        return CustomerMenuDTO.MenuProductDTO.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .imageUrl(product.getImageUrl())
                .price(isOnSale ? product.getDiscountedPrice() : product.getPrice())
                .originalPrice(originalPrice)
                .isOnSale(isOnSale)
                .saleLabel(saleLabel)
                .isAvailable(product.getIsAvailable())
                .isPopular(product.getIsPopular())
                .isNew(product.getIsNew())
                .dietaryTags(product.getDietaryTags())
                .allergens(product.getAllergens())
                .calories(product.getCalories())
                .preparationTime(product.getPreparationTime())
                .variants(mapVariants(product))
                .addOnGroups(mapAddOnGroups(product))
                .build();
    }

    private List<CustomerMenuDTO.ProductVariantDTO> mapVariants(Product product) {
        if (product.getVariants() == null) {
            return List.of();
        }

        return product.getVariants().stream()
                .filter(ProductVariant::getIsActive)
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
                .filter(AddOnGroup::getIsActive)
                .map(group -> CustomerMenuDTO.ProductAddOnGroupDTO.builder()
                        .id(group.getId())
                        .name(group.getName())
                        .description(group.getDescription())
                        .isRequired(group.getIsRequired())
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
                .filter(AddOn::getIsActive)
                .map(addOn -> CustomerMenuDTO.ProductAddOnDTO.builder()
                        .id(addOn.getId())
                        .name(addOn.getName())
                        .price(addOn.getPrice())
                        .isAvailable(addOn.getIsAvailable())
                        .build())
                .collect(Collectors.toList());
    }

    private List<CustomerMenuDTO.FeaturedProductDTO> assembleFeaturedProducts(Long restaurantId) {
        return productRepository.findFeaturedByRestaurantId(restaurantId)
                .stream()
                .limit(6)
                .map(product -> CustomerMenuDTO.FeaturedProductDTO.builder()
                        .productId(product.getId())
                        .name(product.getName())
                        .imageUrl(product.getImageUrl())
                        .price(product.getPrice())
                        .tagline(product.getShortDescription())
                        .build())
                .collect(Collectors.toList());
    }

    private List<CustomerMenuDTO.PromotionBannerDTO> assemblePromotions(Long restaurantId) {
        LocalDateTime now = LocalDateTime.now();

        return promotionRepository.findActivePromotionsByRestaurantId(restaurantId, now)
                .stream()
                .limit(3)
                .map(promotion -> CustomerMenuDTO.PromotionBannerDTO.builder()
                        .id(promotion.getId())
                        .title(promotion.getName())
                        .description(promotion.getDescription())
                        .imageUrl(promotion.getBannerImageUrl())
                        .promoCode(promotion.getCode())
                        .build())
                .collect(Collectors.toList());
    }
}
