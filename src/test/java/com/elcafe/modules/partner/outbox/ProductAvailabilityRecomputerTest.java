package com.elcafe.modules.partner.outbox;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.elcafe.modules.inventory.entity.ProductIngredient;
import com.elcafe.modules.inventory.repository.InventoryProductIngredientRepository;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Which dishes the kitchen can still make, and when that is worth saying out loud.
 *
 * <p>Two rules here are easy to break in a later refactor and expensive when broken. A product with
 * no recipe rows must stay on sale — treating "we know nothing about its ingredients" as "it is sold
 * out" would empty the menu of any venue that never entered recipes. And a deduction that does not
 * cross a threshold must stay silent, or a busy kitchen sends a partner one message per sale saying
 * nothing changed.
 *
 * <p>Who hears about a flip, and what the message says, is {@link PartnerMenuNotifier}'s job and is
 * tested there — this class only cares that a flip is announced at all.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductAvailabilityRecomputerTest {

    @Mock private InventoryProductIngredientRepository productIngredientRepository;
    @Mock private ProductRepository productRepository;
    @Mock private PartnerMenuNotifier notifier;
    @InjectMocks private ProductAvailabilityRecomputer recomputer;

    private Category category;

    @BeforeEach
    void setUp() {
        Restaurant restaurant = Restaurant.builder().name("Test Cafe").build();
        restaurant.setId(7L);
        category = Category.builder().restaurant(restaurant).name("Main").build();
        category.setId(1L);
    }

    private Product product(long id, boolean recipeAvailable) {
        Product product = Product.builder()
                .category(category).name("Osh " + id).price(new BigDecimal("30000"))
                .status(ProductStatus.LIVE).inStock(true).recipeAvailable(recipeAvailable).build();
        product.setId(id);
        return product;
    }

    private Ingredient ingredient(long id, String stock) {
        Ingredient ingredient = Ingredient.builder()
                .name("Beef " + id).unit("kg")
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
    @DisplayName("a short non-optional ingredient takes the dish off the menu and says so")
    void shortIngredient_flipsProductUnavailable() {
        Product osh = product(10L, true);
        when(productRepository.findAllById(Set.of(10L))).thenReturn(List.of(osh));
        stubRecipeRows(List.of(recipeRow(osh, ingredient(1L, "0.2"), "0.5", false)));

        recomputer.recompute(Set.of(10L));

        assertThat(osh.getRecipeAvailable()).isFalse();
        verify(productRepository).saveAll(List.of(osh));
        verify(notifier).productAvailabilityChanged(osh);
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
        verifyNoInteractions(notifier);
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
        verifyNoInteractions(notifier);
    }

    @Test
    @DisplayName("restocking the ingredient puts the dish back and says so")
    void restock_flipsProductBackAvailable() {
        Product osh = product(10L, false);
        when(productRepository.findAllById(Set.of(10L))).thenReturn(List.of(osh));
        stubRecipeRows(List.of(recipeRow(osh, ingredient(1L, "20"), "0.5", false)));

        recomputer.recompute(Set.of(10L));

        assertThat(osh.getRecipeAvailable()).isTrue();
        verify(productRepository).saveAll(List.of(osh));
        verify(notifier).productAvailabilityChanged(osh);
    }

    @Test
    @DisplayName("a deduction that crosses no threshold writes nothing and says nothing")
    void noFlip_writesNothingAndPublishesNothing() {
        Product osh = product(10L, true);
        when(productRepository.findAllById(Set.of(10L))).thenReturn(List.of(osh));
        stubRecipeRows(List.of(recipeRow(osh, ingredient(1L, "20"), "0.5", false)));

        recomputer.recompute(Set.of(10L));

        verify(productRepository, never()).saveAll(any());
        verifyNoInteractions(notifier);
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
        verify(notifier).productAvailabilityChanged(osh);
        verify(notifier, never()).productAvailabilityChanged(lagman);
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

        verifyNoInteractions(productRepository, notifier);
    }
}
