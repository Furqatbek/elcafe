package com.elcafe.modules.partner.outbox;

import com.elcafe.modules.menu.entity.Category;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Who gets told about a menu change, and what we tell them.
 *
 * <p>The number matters most. A partner displays what we publish and sends it back as the expected
 * total on every order, so a notification carrying the counter price instead of the channel price
 * would undo the venue's markup on every item anyone edits — and then refuse the resulting orders for
 * a mismatch neither side can see the cause of.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PartnerMenuNotifierTest {

    private static final long RESTAURANT_ID = 7L;
    private static final long PARTNER_ID = 99L;

    @Mock private PartnerRestaurantRepository partnerRestaurantRepository;
    @Mock private PartnerRepository partnerRepository;
    @Mock private PartnerPricingService pricingService;
    @Mock private PartnerEventPublisher publisher;
    @InjectMocks private PartnerMenuNotifier notifier;

    private Category category;
    private Partner partner;

    @BeforeEach
    void setUp() {
        com.elcafe.modules.restaurant.entity.Restaurant restaurant =
                com.elcafe.modules.restaurant.entity.Restaurant.builder().name("Test Cafe").build();
        restaurant.setId(RESTAURANT_ID);
        category = Category.builder().restaurant(restaurant).name("Main").build();
        category.setId(1L);

        partner = Partner.builder().name("ZBR").slug("zbr").active(true).build();
        partner.setId(PARTNER_ID);

        grantMenuAccess(true, true);
        when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));
        when(pricingService.resolverFor(PARTNER_ID, RESTAURANT_ID))
                .thenReturn(PartnerPriceResolver.passThrough());
    }

    private void grantMenuAccess(boolean active, boolean canReadMenu) {
        PartnerRestaurant grant = PartnerRestaurant.builder()
                .partnerId(PARTNER_ID).restaurantId(RESTAURANT_ID)
                .canReadMenu(canReadMenu).canPushOrders(true).active(active).build();
        when(partnerRestaurantRepository.findByRestaurantId(RESTAURANT_ID)).thenReturn(List.of(grant));
    }

    private Product product(String price) {
        Product product = Product.builder()
                .category(category).name("Osh").price(new BigDecimal(price))
                .status(ProductStatus.LIVE).inStock(true).recipeAvailable(true)
                .variants(new ArrayList<>()).build();
        product.setId(10L);
        return product;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturePayload(IntegrationEventType type, String subject) {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publish(eq(partner), eq(RESTAURANT_ID), eq(type), eq(subject),
                payload.capture());
        return (Map<String, Object>) payload.getValue();
    }

    @Test
    @DisplayName("a price change goes out at the partner's channel price, not the counter price")
    void priceChange_usesTheChannelPrice() {
        // The resolver's arithmetic is PartnerPriceResolverTest's subject. What matters here is that
        // this asks it at all rather than reaching for product.getPrice() — reading the counter price
        // would quietly strip the venue's markup from every item anyone edits.
        PartnerPriceResolver markedUp = org.mockito.Mockito.mock(PartnerPriceResolver.class);
        when(markedUp.forProduct(any())).thenReturn(new BigDecimal("34500"));
        when(pricingService.resolverFor(PARTNER_ID, RESTAURANT_ID)).thenReturn(markedUp);

        notifier.productPriceChanged(product("30000"));

        Map<String, Object> payload = capturePayload(IntegrationEventType.MENU_ITEM_CHANGED, "product:10");
        assertThat((BigDecimal) payload.get("price")).isEqualByComparingTo("34500");
        assertThat((BigDecimal) payload.get("priceWithMargin")).isEqualByComparingTo("34500");
    }

    @Test
    @DisplayName("price is sent under both keys, so an importer cannot add a second margin")
    void priceChange_sendsBothKeys() {
        notifier.productPriceChanged(product("30000"));

        Map<String, Object> payload = capturePayload(IntegrationEventType.MENU_ITEM_CHANGED, "product:10");
        assertThat((BigDecimal) payload.get("price")).isEqualByComparingTo("30000");
        assertThat((BigDecimal) payload.get("priceWithMargin")).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("variant prices ride along, because they are the price once variants exist")
    void priceChange_carriesVariants() {
        Product osh = product("30000");
        ProductVariant large = ProductVariant.builder()
                .product(osh).name("Large").price(new BigDecimal("38000"))
                .inStock(true).isAvailable(true).build();
        large.setId(11L);
        osh.getVariants().add(large);

        notifier.productPriceChanged(osh);

        Map<String, Object> payload = capturePayload(IntegrationEventType.MENU_ITEM_CHANGED, "product:10");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> variants = (List<Map<String, Object>>) payload.get("variants");
        assertThat(variants).singleElement().satisfies(variant -> {
            assertThat(variant).containsEntry("variantId", 11L).containsEntry("name", "Large");
            assertThat((BigDecimal) variant.get("price")).isEqualByComparingTo("38000");
            assertThat((BigDecimal) variant.get("priceWithMargin")).isEqualByComparingTo("38000");
        });
    }

    @Test
    @DisplayName("an item that is not live has no price worth correcting")
    void priceChange_onDraftItem_saysNothing() {
        Product draft = product("30000");
        draft.setStatus(ProductStatus.DRAFT);

        notifier.productPriceChanged(draft);

        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("availability is the effective answer, whichever half of it moved")
    void availability_reportsTheEffectiveAnswer() {
        Product osh = product("30000");
        osh.setInStock(false);

        notifier.productAvailabilityChanged(osh);

        Map<String, Object> payload =
                capturePayload(IntegrationEventType.MENU_ITEM_AVAILABILITY, "product:10");
        assertThat(payload).containsEntry("available", false).containsEntry("productId", 10L);
    }

    @Test
    @DisplayName("withdrawing an item from the menu reads as unavailable, not as silence")
    void availability_onWithdrawnItem_saysUnavailable() {
        // Their importer has no deletion path, so an item that merely vanished from our menu would
        // stay orderable on theirs. Saying "unavailable" out loud is what takes it off sale.
        Product withdrawn = product("30000");
        withdrawn.setStatus(ProductStatus.DRAFT);

        notifier.productAvailabilityChanged(withdrawn);

        Map<String, Object> payload =
                capturePayload(IntegrationEventType.MENU_ITEM_AVAILABILITY, "product:10");
        assertThat(payload).containsEntry("available", false);
    }

    @Test
    @DisplayName("a partner whose menu grant is revoked hears nothing")
    void revokedGrant_isNotNotified() {
        grantMenuAccess(true, false);

        notifier.productAvailabilityChanged(product("30000"));
        notifier.productPriceChanged(product("30000"));

        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("a deactivated partner hears nothing")
    void inactivePartner_isNotNotified() {
        partner.setActive(false);

        notifier.productAvailabilityChanged(product("30000"));

        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("a markup change is one message for the venue, not one per dish")
    void venuePricingChange_isASingleMessage() {
        notifier.venuePricingChanged(PARTNER_ID, RESTAURANT_ID);

        Map<String, Object> payload =
                capturePayload(IntegrationEventType.MENU_PRICES_CHANGED, "menu:" + RESTAURANT_ID);
        assertThat(payload)
                .containsEntry("restaurantId", RESTAURANT_ID)
                .containsEntry("reason", "CHANNEL_PRICING_CHANGED");
    }

    @Test
    @DisplayName("a markup change for a partner who cannot read the menu goes nowhere")
    void venuePricingChange_needsMenuAccess() {
        grantMenuAccess(true, false);

        notifier.venuePricingChanged(PARTNER_ID, RESTAURANT_ID);

        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("a product with no venue behind it cannot be addressed to anyone")
    void productWithoutRestaurant_isSkipped() {
        Product orphan = Product.builder().name("Orphan").price(BigDecimal.ONE)
                .status(ProductStatus.LIVE).inStock(true).recipeAvailable(true).build();
        orphan.setId(99L);

        notifier.productAvailabilityChanged(orphan);

        verify(publisher, org.mockito.Mockito.never())
                .publish(any(), anyLong(), any(), any(), any());
    }
}
