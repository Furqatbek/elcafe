package com.elcafe.modules.partner.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.menu.entity.AddOn;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductVariant;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.partner.dto.PartnerMenuResponse;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the partner-facing menu payload.
 *
 * <p>Not cached, unlike {@code MenuService.getPublicMenu}. The storefront tolerates a slightly stale
 * menu because a customer who orders a sold-out item gets told so at checkout; a partner's copy is what
 * their customers browse for the next hour, so a stale availability flag turns into an order for
 * something the kitchen cannot make. If this ever needs caching it should be short-TTL and evicted on
 * stock changes, not bolted onto the existing {@code "menu"} cache whose evictions only track edits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerMenuService {

    private final RestaurantRepository restaurantRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public PartnerMenuResponse getMenu(Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));

        List<Category> categories =
                categoryRepository.findByRestaurant_IdAndActiveTrueOrderBySortOrder(restaurantId);

        // Tracks the freshest timestamp seen anywhere in the tree, so the partner gets one value to
        // compare instead of having to diff the whole payload.
        LocalDateTime[] version = {restaurant.getUpdatedAt()};

        List<PartnerMenuResponse.Category> payload = new ArrayList<>();
        for (Category category : categories) {
            version[0] = latest(version[0], category.getUpdatedAt());

            List<PartnerMenuResponse.Product> products = new ArrayList<>();
            for (Product product : category.getProducts()) {
                version[0] = latest(version[0], product.getUpdatedAt());
                products.add(toProduct(product));
            }
            products.sort(Comparator.comparing(
                    PartnerMenuResponse.Product::getSortOrder, Comparator.nullsLast(Integer::compareTo)));

            payload.add(PartnerMenuResponse.Category.builder()
                    .id(category.getId())
                    .name(category.getName())
                    .description(category.getDescription())
                    .imageUrl(category.getImageUrl())
                    .sortOrder(category.getSortOrder())
                    .products(products)
                    .build());
        }

        return PartnerMenuResponse.builder()
                .restaurantId(restaurant.getId())
                .restaurantName(restaurant.getName())
                .acceptingOrders(restaurant.getAcceptingOrders())
                .deliveryFee(restaurant.getDeliveryFee())
                .currency("UZS")
                .menuVersion(version[0])
                .categories(payload)
                .build();
    }

    private PartnerMenuResponse.Product toProduct(Product product) {
        List<PartnerMenuResponse.Variant> variants = product.getVariants().stream()
                .sorted(Comparator.comparing(ProductVariant::getSortOrder,
                        Comparator.nullsLast(Integer::compareTo)))
                .map(variant -> PartnerMenuResponse.Variant.builder()
                        .id(variant.getId())
                        .name(variant.getName())
                        .description(variant.getDescription())
                        .price(variant.getPrice())
                        .sku(variant.getSku())
                        .sortOrder(variant.getSortOrder())
                        // A variant is orderable only if it is both in stock and marked available —
                        // the two flags are maintained by different screens and either one can 86 it.
                        .available(Boolean.TRUE.equals(variant.getInStock())
                                && Boolean.TRUE.equals(variant.getIsAvailable()))
                        .build())
                .toList();

        List<PartnerMenuResponse.AddOnGroup> groups = product.getAddOnGroups().stream()
                .filter(group -> Boolean.TRUE.equals(group.getActive()))
                .map(group -> PartnerMenuResponse.AddOnGroup.builder()
                        .id(group.getId())
                        .name(group.getName())
                        .description(group.getDescription())
                        .required(group.getRequired())
                        .minSelection(group.getMinSelection())
                        .maxSelection(group.getMaxSelection())
                        .addOns(group.getAddOns().stream()
                                .sorted(Comparator.comparing(AddOn::getSortOrder,
                                        Comparator.nullsLast(Integer::compareTo)))
                                .map(addOn -> PartnerMenuResponse.AddOn.builder()
                                        .id(addOn.getId())
                                        .name(addOn.getName())
                                        .description(addOn.getDescription())
                                        .price(addOn.getPrice())
                                        .sortOrder(addOn.getSortOrder())
                                        .available(addOn.getAvailable())
                                        .build())
                                .toList())
                        .build())
                .toList();

        return PartnerMenuResponse.Product.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .imageUrl(product.getImageUrl())
                .price(product.getPrice())
                .itemType(product.getItemType())
                .sortOrder(product.getSortOrder())
                .available(Boolean.TRUE.equals(product.getInStock()))
                .soldByWeight(product.getIsSoldByWeight())
                .weightUnit(product.getWeightUnit())
                .minWeight(product.getMinWeight())
                .maxWeight(product.getMaxWeight())
                .variants(variants)
                .addOnGroups(groups)
                .build();
    }

    private LocalDateTime latest(LocalDateTime current, LocalDateTime candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isAfter(current) ? candidate : current;
    }
}
