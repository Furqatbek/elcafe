package com.elcafe.modules.partner.outbox;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.ProductIngredient;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.partner.entity.Partner;
import com.elcafe.modules.partner.entity.PartnerRestaurant;
import com.elcafe.modules.partner.enums.IntegrationEventType;
import com.elcafe.modules.partner.repository.PartnerRepository;
import com.elcafe.modules.partner.repository.PartnerRestaurantRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The rules that decide whether the kitchen can still make a dish, and who hears about it.
 *
 * <p>Two of these are easy to break in a later refactor and expensive when broken. A product with no
 * recipe rows must stay on sale — treating "we know nothing about its ingredients" as "it is sold out"
 * would empty the menu of any venue that never entered recipes. And a deduction that does not cross a
 * threshold must stay silent, or a busy kitchen sends a partner one message per sale saying nothing
 * changed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductAvailabilityRecomputerTest {

    private static final long RESTAURANT_ID = 7L;

    @Mock private InventoryProductIngredientRepository productIngredientRepository;
    @Mock private ProductRepository productRepository;
    @Mock private PartnerRestaurantRepository partnerRestaurantRepository;
    @Mock private PartnerRepository partnerRepository;
    @Mock private PartnerEventPublisher publisher;
    @InjectMocks private ProductAvailabilityRecomputer recomputer;

    private Restaurant restaurant;
    private Category category;
    private Partner partner;

    @BeforeEach
    void setUp() {
        restaurant = Restaurant.builder().name("Test Cafe").build();
        restaurant.setId(RESTAURANT_ID);
        category = Category.builder().restaurant(restaurant).name("Main").build();
        category.setId(1L);

        partner = Partner.builder().name("ZBR").slug("zbr").active(true).build();
        partner.setId(99L);

        PartnerRestaurant grant = PartnerRestaurant.builder()
                .partnerId(99L).restaurantId(RESTAURANT_ID)
                .canReadMenu(true).canPushOrders(true).active(true).build();
        when(partnerRestaurantRepository.findByRestaurantId(RESTAURANT_ID)).thenReturn(List.of(grant));
        when(partnerRepository.findById(99L)).thenReturn(java.util.Optional.of(partner));
    }

    private Product product(long id, boolean recipeAvailable) {
        Product product = Product.builder()
                .category(category).name("Osh " + id).price(new BigDecimal("30000"))
                .inStock(true).recipeAvailable(recipeAvailable).build();
        product.setId(id);
        return product;
    }

    private Ingredient ingredient(long id, String stock) {
        Ingredient ingredient = Ingredient.builder()
                .restaurant(restaurant).name("Beef " + id).unit("kg")
                .currentStock(new BigDecimal(stock)).trackInventory(true).build();
        ingredient.setId(id);
        return ingredient;
    }

    private ProductIngredient recipeRow(Product product, Ingredient ingredient, String required,
                                        boolean optional) {
        return ProductIngredient.builder()
                .product(product).ingredient(ingredient)
                .quantityRequired(new BigDecimal(required)).optional(optional).build();
    }

    @SuppressWarnings("unchecked")
    private void stubRecipeRows(List<ProductIngredient> rows) {
        when(productIngredientRepository.findByProductIdInWithIngredients(any(Collection.class)))
                .thenReturn(rows);
    }

    @Test
    @DisplayName("a short non-optional ingredient takes the dish off the menu and tells the partner")
    void shortIngredient_flipsProductUnavailable() {
        Product osh = product(10L, true);
        when(productRepository.findAllById(Set.of(10L))).thenReturn(List.of(osh));
        stubRecipeRows(List.of(recipeRow(osh, ingredient(1L, "0.2"), "0.5", false)));

        recomputer.recompute(Set.of(10L));

        assertThat(osh.getRecipeAvailable()).isFalse();
        verify(productRepository).saveAll(List.of(osh));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publish(eq(partner), eq(RESTAURANT_ID),
                eq(IntegrationEventType.MENU_ITEM_AVAILABILITY), eq("product:10"), payload.capture());
        assertThat((Map<String, Object>) payload.getValue())
                .containsEntry("productId", 10L)
                .containsEntry("available", false);
    }

    @Test
    @DisplayName("an optional ingredient running out is a missing garnish, not a sold-out dish")
    void optionalIngredient_doesNotFlipProduct() {
        Product osh = product(10L, true);
        when(productRepository.findAllById(Set.of(10L))).thenReturn(List.of(osh));
        stubRecipeRows(List.of(recipeRow(osh, ingredient(1L, "0"), "0.5", true)));

        recomputer.recompute(Set.of(10L));

        assertThat(osh.getRecipeAvailable()).isTrue();
        verify(productRepository, never()).saveAll(any());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("a product with no recipe rows stays makeable")
    void productWithoutRecipe_staysAvailable() {
        Product mineralWater = product(11L, true);
        when(productRepository.findAllById(Set.of(11L))).thenReturn(List.of(mineralWater));
        stubRecipeRows(List.of());

        recomputer.recompute(Set.of(11L));

        assertThat(mineralWater.getRecipeAvailable()).isTrue();
        verify(productRepository, never()).saveAll(any());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("restocking the ingredient puts the dish back and tells the partner")
    void restock_flipsProductBackAvailable() {
        Product osh = product(10L, false);
        when(productRepository.findAllById(Set.of(10L))).thenReturn(List.of(osh));
        stubRecipeRows(List.of(recipeRow(osh, ingredient(1L, "20"), "0.5", false)));

        recomputer.recompute(Set.of(10L));

        assertThat(osh.getRecipeAvailable()).isTrue();
        verify(productRepository).saveAll(List.of(osh));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publish(any(), anyLong(), eq(IntegrationEventType.MENU_ITEM_AVAILABILITY),
                eq("product:10"), payload.capture());
        assertThat((Map<String, Object>) payload.getValue()).containsEntry("available", true);
    }

    @Test
    @DisplayName("a deduction that crosses no threshold writes nothing and says nothing")
    void noFlip_writesNothingAndPublishesNothing() {
        Product osh = product(10L, true);
        when(productRepository.findAllById(Set.of(10L))).thenReturn(List.of(osh));
        stubRecipeRows(List.of(recipeRow(osh, ingredient(1L, "20"), "0.5", false)));

        recomputer.recompute(Set.of(10L));

        verify(productRepository, never()).saveAll(any());
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("many products are decided from a single recipe query, not one query each")
    void manyProducts_useOneQuery() {
        Product osh = product(10L, true);
        Product lagman = product(11L, true);
        Product somsa = product(12L, true);
        when(productRepository.findAllById(any())).thenReturn(List.of(osh, lagman, somsa));

        Ingredient beef = ingredient(1L, "0.1");
        stubRecipeRows(List.of(
                recipeRow(osh, beef, "0.5", false),
                recipeRow(lagman, beef, "0.05", false),
                recipeRow(somsa, ingredient(2L, "5"), "0.2", false)));

        recomputer.recompute(Set.of(10L, 11L, 12L));

        verify(productIngredientRepository, times(1)).findByProductIdInWithIngredients(any());
        // Only the one that actually ran short moved; lagman needs less beef than is left.
        assertThat(osh.getRecipeAvailable()).isFalse();
        assertThat(lagman.getRecipeAvailable()).isTrue();
        assertThat(somsa.getRecipeAvailable()).isTrue();
        verify(productRepository).saveAll(List.of(osh));
    }

    @Test
    @DisplayName("a dish the kitchen can make but a manager turned off is still reported unavailable")
    void manualSwitchOff_isReflectedInThePayload() {
        Product osh = product(10L, false);
        osh.setInStock(false);
        when(productRepository.findAllById(Set.of(10L))).thenReturn(List.of(osh));
        stubRecipeRows(List.of(recipeRow(osh, ingredient(1L, "20"), "0.5", false)));

        recomputer.recompute(Set.of(10L));

        // The recipe flag flips back — the ingredients are there — but the partner is told the
        // effective answer, which is still "no" because a person switched it off.
        assertThat(osh.getRecipeAvailable()).isTrue();
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(publisher).publish(any(), anyLong(), any(), eq("product:10"), payload.capture());
        assertThat((Map<String, Object>) payload.getValue()).containsEntry("available", false);
    }

    @Test
    @DisplayName("a partner whose menu grant is revoked hears nothing")
    void revokedGrant_isNotNotified() {
        PartnerRestaurant revoked = PartnerRestaurant.builder()
                .partnerId(99L).restaurantId(RESTAURANT_ID)
                .canReadMenu(false).canPushOrders(true).active(true).build();
        when(partnerRestaurantRepository.findByRestaurantId(RESTAURANT_ID)).thenReturn(List.of(revoked));

        Product osh = product(10L, true);
        when(productRepository.findAllById(Set.of(10L))).thenReturn(List.of(osh));
        stubRecipeRows(List.of(recipeRow(osh, ingredient(1L, "0"), "0.5", false)));

        recomputer.recompute(Set.of(10L));

        assertThat(osh.getRecipeAvailable()).isFalse();
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("stock moving is turned into the dishes that use it, via the reverse index")
    void recomputeForIngredients_walksTheReverseIndex() {
        Product osh = product(10L, true);
        Ingredient beef = ingredient(1L, "0");
        when(productIngredientRepository.findByIngredientIdWithProduct(1L))
                .thenReturn(List.of(recipeRow(osh, beef, "0.5", false)));
        when(productRepository.findAllById(Set.of(10L))).thenReturn(List.of(osh));
        stubRecipeRows(List.of(recipeRow(osh, beef, "0.5", false)));

        recomputer.recomputeForIngredients(List.of(1L));

        assertThat(osh.getRecipeAvailable()).isFalse();
        verify(productRepository).saveAll(List.of(osh));
    }

    @Test
    @DisplayName("nothing moved, nothing to do")
    void emptyInput_doesNothing() {
        recomputer.recomputeForIngredients(List.of());
        recomputer.recompute(Set.of());

        verifyNoInteractions(productRepository, publisher);
    }
}
