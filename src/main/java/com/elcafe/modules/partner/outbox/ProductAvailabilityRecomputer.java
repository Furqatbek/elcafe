package com.elcafe.modules.partner.outbox;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.ProductIngredient;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.entity.PartnerRestaurant;
import com.elcafe.modules.partner.enums.IntegrationEventType;
import com.elcafe.modules.partner.repository.PartnerRepository;
import com.elcafe.modules.partner.repository.PartnerRestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Works out which dishes the kitchen can still make, writes the answer to
 * {@code products.recipe_available}, and queues a message to partners for the ones that moved.
 *
 * <p>This exists because of one asymmetry. On our own screens, selling something the kitchen cannot
 * make is an annoyance: the order is refused at the accept step and a human sorts it out. On an
 * aggregator the customer has already paid, so the same mistake costs a refund and a bad review. The
 * kitchen's actual capacity has to reach their menu before their customer orders, not after.
 *
 * <p><b>Cost is the design constraint.</b> A single order acceptance can move five ingredients used
 * across eighty dishes, so this never asks "can we make this?" product by product — that would be
 * eighty queries. It walks the reverse index once to find the affected products, loads all their
 * recipe rows in a single query, and decides every product from that one result set.
 *
 * <p>Separate bean from {@link ProductAvailabilityService} on purpose: that one runs inside the
 * caller's transaction and must not be able to fail it, this one needs a transaction of its own.
 * Folding them together would mean a self-invoked {@code @Transactional} method, which Spring
 * silently ignores.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductAvailabilityRecomputer {

    private final InventoryProductIngredientRepository productIngredientRepository;
    private final ProductRepository productRepository;
    private final PartnerRestaurantRepository partnerRestaurantRepository;
    private final PartnerRepository partnerRepository;
    private final PartnerEventPublisher publisher;

    /**
     * Recompute every product that uses any of these ingredients.
     *
     * <p>The entry point for stock movements: a deduction or a delivery knows which ingredients moved,
     * not which dishes they belong to.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recomputeForIngredients(Collection<Long> ingredientIds) {
        if (ingredientIds == null || ingredientIds.isEmpty()) {
            return;
        }
        Set<Long> affected = new HashSet<>();
        for (Long ingredientId : ingredientIds) {
            productIngredientRepository.findByIngredientIdWithProduct(ingredientId)
                    .forEach(row -> affected.add(row.getProduct().getId()));
        }
        recompute(affected);
    }

    /**
     * Recompute these products and publish the ones that changed.
     *
     * <p>Publishing only on a flip is what keeps this quiet. A busy kitchen deducts stock on every
     * order; almost none of those deductions cross a threshold, and a partner does not want a message
     * per sale telling them nothing changed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recompute(Collection<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }

        List<Product> products = productRepository.findAllById(productIds);
        if (products.isEmpty()) {
            return;
        }

        Map<Long, Boolean> makeable = computeMakeable(products);

        List<Product> flipped = new ArrayList<>();
        for (Product product : products) {
            boolean now = makeable.getOrDefault(product.getId(), true);
            if (!Boolean.valueOf(now).equals(product.getRecipeAvailable())) {
                product.setRecipeAvailable(now);
                flipped.add(product);
            }
        }

        if (flipped.isEmpty()) {
            return;
        }

        productRepository.saveAll(flipped);
        log.info("Recipe availability changed for {} product(s)", flipped.size());
        flipped.forEach(this::publishAvailability);
    }

    /**
     * Decides every product in one pass over one query.
     *
     * <p>A product is makeable unless some non-optional ingredient is short. Products with no recipe
     * rows never appear in the result and so stay makeable, which is the honest answer: we know
     * nothing about their ingredients, and guessing "unavailable" would empty a menu that had simply
     * never had recipes entered.
     */
    private Map<Long, Boolean> computeMakeable(List<Product> products) {
        Map<Long, Boolean> makeable = new LinkedHashMap<>();
        products.forEach(product -> makeable.put(product.getId(), true));

        List<ProductIngredient> recipeRows =
                productIngredientRepository.findByProductIdInWithIngredients(makeable.keySet());

        for (ProductIngredient row : recipeRows) {
            if (Boolean.TRUE.equals(row.getOptional())) {
                // An optional ingredient being out is a garnish missing, not a dish off the menu.
                continue;
            }
            Ingredient ingredient = row.getIngredient();
            BigDecimal required = row.getQuantityRequired() == null ? BigDecimal.ZERO : row.getQuantityRequired();
            if (ingredient == null || !ingredient.hasStock(required)) {
                makeable.put(row.getProduct().getId(), false);
            }
        }
        return makeable;
    }

    /** Tell every partner that can see this venue's menu. */
    private void publishAvailability(Product product) {
        Long restaurantId = product.getCategory() != null && product.getCategory().getRestaurant() != null
                ? product.getCategory().getRestaurant().getId()
                : null;
        if (restaurantId == null) {
            log.warn("Product {} has no restaurant — cannot notify partners of availability",
                    product.getId());
            return;
        }

        for (PartnerRestaurant grant : partnerRestaurantRepository.findByRestaurantId(restaurantId)) {
            if (!Boolean.TRUE.equals(grant.getActive()) || !Boolean.TRUE.equals(grant.getCanReadMenu())) {
                continue;
            }
            partnerRepository.findById(grant.getPartnerId())
                    .filter(partner -> Boolean.TRUE.equals(partner.getActive()))
                    .ifPresent(partner -> publish(partner, restaurantId, product));
        }
    }

    private void publish(Partner partner, Long restaurantId, Product product) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("productId", product.getId());
        payload.put("name", product.getName());
        // The effective answer, not the raw flag: a partner should never have to know that our
        // availability is two booleans, nor which of them moved.
        payload.put("available", product.isOrderable());

        publisher.publish(partner, restaurantId, IntegrationEventType.MENU_ITEM_AVAILABILITY,
                // Subject is the product, so an item flapping across its threshold collapses to one
                // message carrying the latest state rather than a contradictory backlog.
                "product:" + product.getId(),
                payload);
    }
}
