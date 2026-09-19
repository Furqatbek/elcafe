package com.elcafe.modules.partner.outbox;

import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductVariant;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.entity.PartnerRestaurant;
import com.elcafe.modules.partner.enums.IntegrationEventType;
import com.elcafe.modules.partner.repository.PartnerRepository;
import com.elcafe.modules.partner.repository.PartnerRestaurantRepository;
import com.elcafe.modules.partner.service.PartnerPriceResolver;
import com.elcafe.modules.partner.service.PartnerPricingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Tells partners when something they are serving from their own catalogue has changed here.
 *
 * <p>A partner that pulls our menu once and caches it is right to do so — re-pulling four hundred
 * items to discover that one price moved is wasteful for both sides. The bargain is that we then owe
 * them the changes, and this is where that debt is paid. Without it their customer orders at
 * yesterday's price, our {@code expectedTotal} check refuses the order, and neither side can see why.
 *
 * <p>Everything a partner can be told about a venue's menu goes through here, so availability and
 * price cannot drift into two different ideas of who is listening or what "available" means. They had
 * started to: the recompute knew about ingredients, but a manager flipping an item off by hand told
 * nobody at all.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerMenuNotifier {

    private final PartnerRestaurantRepository partnerRestaurantRepository;
    private final PartnerRepository partnerRepository;
    private final PartnerPricingService pricingService;
    private final PartnerEventPublisher publisher;

    /**
     * Whether a partner should be selling this at all.
     *
     * <p>One definition, because the partner menu endpoint filters on exactly this and a notification
     * that disagreed with it would be worse than none — the partner would hide an item we are happily
     * serving, or keep selling one we have withdrawn.
     */
    public static boolean orderableByPartner(Product product) {
        return product != null && product.getStatus() == ProductStatus.LIVE && product.isOrderable();
    }

    /**
     * An item became orderable, or stopped being.
     *
     * <p>Covers all three ways that happens: an ingredient ran short, a manager flipped the switch, or
     * the item was withdrawn from the menu entirely. A partner does not care which — and telling them
     * which would leak a distinction they cannot act on.
     */
    public void productAvailabilityChanged(Product product) {
        forEachPartner(product, (partner, restaurantId) -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("productId", product.getId());
            payload.put("name", product.getName());
            payload.put("available", orderableByPartner(product));

            publisher.publish(partner, restaurantId, IntegrationEventType.MENU_ITEM_AVAILABILITY,
                    // Subject is the product, so an item flapping across its threshold collapses to one
                    // message carrying the latest state rather than a contradictory backlog.
                    "product:" + product.getId(), payload);
        });
    }

    /**
     * A price moved — the item's own, or one of its variants'.
     *
     * <p>Sent per partner at that partner's channel price, never the counter price. The whole point of
     * channel pricing is that these differ, and a notification carrying the base price would quietly
     * undo the markup on every item anyone edits.
     */
    public void productPriceChanged(Product product) {
        if (product.getStatus() != ProductStatus.LIVE) {
            // Never on their menu, so nothing to correct. A draft becoming live is an availability
            // change, and goes out as one.
            return;
        }
        forEachPartner(product, (partner, restaurantId) -> {
            PartnerPriceResolver resolver = pricingService.resolverFor(partner.getId(), restaurantId);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("productId", product.getId());
            payload.put("name", product.getName());
            putPrice(payload, resolver.forProduct(product));
            payload.put("available", orderableByPartner(product));
            payload.put("variants", variantPrices(product, resolver));

            publisher.publish(partner, restaurantId, IntegrationEventType.MENU_ITEM_CHANGED,
                    "product:" + product.getId(), payload);
        });
    }

    /**
     * One partner's markup for a whole venue changed, so every price they hold is wrong at once.
     *
     * <p>Deliberately one message rather than one per item. A venue-wide markup touching four hundred
     * items would otherwise queue four hundred deliveries for a single click, which is the exact
     * traffic ZBR asked us to avoid when they proposed a bulk path. This says "your prices for this
     * venue moved, re-read the menu", which is both cheaper and impossible to apply by halves.
     */
    public void venuePricingChanged(Long partnerId, Long restaurantId) {
        partnerRepository.findById(partnerId)
                .filter(partner -> Boolean.TRUE.equals(partner.getActive()))
                .ifPresent(partner -> {
                    if (!canReadMenu(partnerId, restaurantId)) {
                        return;
                    }
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("restaurantId", restaurantId);
                    payload.put("reason", "CHANNEL_PRICING_CHANGED");

                    publisher.publish(partner, restaurantId, IntegrationEventType.MENU_PRICES_CHANGED,
                            // Subject is the venue: a manager adjusting a markup three times in a
                            // minute should cost one re-read, not three.
                            "menu:" + restaurantId, payload);
                });
    }

    /**
     * Both keys, carrying the same number.
     *
     * <p>ZBR's importer uses {@code priceWithMargin} exactly as sent, and applies a margin of its own
     * when only {@code price} is present. Sending one field would therefore be a bet on which one they
     * read; sending both, equal, means the number we publish is the number their customer pays
     * whichever way their importer is written. It is also already true — a channel price has our
     * markup in it, so there is nothing further to add.
     */
    private void putPrice(Map<String, Object> payload, BigDecimal channelPrice) {
        payload.put("price", channelPrice);
        payload.put("priceWithMargin", channelPrice);
    }

    private List<Map<String, Object>> variantPrices(Product product, PartnerPriceResolver resolver) {
        List<Map<String, Object>> variants = new ArrayList<>();
        if (product.getVariants() == null) {
            return variants;
        }
        for (ProductVariant variant : product.getVariants()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("variantId", variant.getId());
            entry.put("name", variant.getName());
            putPrice(entry, resolver.forVariant(variant, product));
            entry.put("available", Boolean.TRUE.equals(variant.getInStock())
                    && Boolean.TRUE.equals(variant.getIsAvailable()));
            variants.add(entry);
        }
        return variants;
    }

    /** Every active partner holding a live menu grant on this product's venue. */
    private void forEachPartner(Product product, BiConsumer<Partner, Long> action) {
        Long restaurantId = restaurantIdOf(product);
        if (restaurantId == null) {
            log.warn("Product {} has no restaurant — cannot notify partners", product.getId());
            return;
        }
        for (PartnerRestaurant grant : partnerRestaurantRepository.findByRestaurantId(restaurantId)) {
            if (!Boolean.TRUE.equals(grant.getActive()) || !Boolean.TRUE.equals(grant.getCanReadMenu())) {
                continue;
            }
            partnerRepository.findById(grant.getPartnerId())
                    .filter(partner -> Boolean.TRUE.equals(partner.getActive()))
                    .ifPresent(partner -> action.accept(partner, restaurantId));
        }
    }

    private boolean canReadMenu(Long partnerId, Long restaurantId) {
        return partnerRestaurantRepository.findByRestaurantId(restaurantId).stream()
                .anyMatch(grant -> grant.getPartnerId().equals(partnerId)
                        && Boolean.TRUE.equals(grant.getActive())
                        && Boolean.TRUE.equals(grant.getCanReadMenu()));
    }

    private Long restaurantIdOf(Product product) {
        return product != null && product.getCategory() != null
                && product.getCategory().getRestaurant() != null
                ? product.getCategory().getRestaurant().getId()
                : null;
    }
}
