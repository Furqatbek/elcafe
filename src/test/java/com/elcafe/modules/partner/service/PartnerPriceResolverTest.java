package com.elcafe.modules.partner.service;

import com.elcafe.modules.menu.entity.AddOn;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductVariant;
import com.elcafe.modules.partner.entity.PartnerPriceRule;
import com.elcafe.modules.partner.entity.PartnerRestaurant;
import com.elcafe.modules.partner.enums.PriceAdjustmentType;
import com.elcafe.modules.partner.enums.PriceRuleScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the arithmetic behind every price a partner sees or is charged.
 *
 * <p>This is the class that decides how much money changes hands, so the cases here are the ones that
 * cost real money when they are wrong: the resolution order between overlapping rules, rounding, and
 * the boundaries where a discount could invert a price.
 */
class PartnerPriceResolverTest {

    private static final Long CATEGORY_ID = 100L;
    private static final Long PRODUCT_ID = 1L;
    private static final Long VARIANT_ID = 11L;

    private PartnerRestaurant grant(PriceAdjustmentType type, String value, String rounding) {
        return PartnerRestaurant.builder()
                .partnerId(7L).restaurantId(3L)
                .priceAdjustmentType(type)
                .priceAdjustmentValue(new BigDecimal(value))
                .priceRounding(new BigDecimal(rounding))
                .build();
    }

    private PartnerPriceRule rule(PriceRuleScope scope, Long targetId,
                                  PriceAdjustmentType type, String value) {
        return PartnerPriceRule.builder()
                .partnerId(7L).restaurantId(3L)
                .scope(scope).targetId(targetId)
                .adjustmentType(type).adjustmentValue(new BigDecimal(value))
                .active(true)
                .build();
    }

    private Product product(String price) {
        Category category = new Category();
        category.setId(CATEGORY_ID);

        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("Osh");
        product.setPrice(new BigDecimal(price));
        product.setCategory(category);
        return product;
    }

    private ProductVariant variant(String price) {
        ProductVariant variant = new ProductVariant();
        variant.setId(VARIANT_ID);
        variant.setName("Large");
        variant.setPrice(new BigDecimal(price));
        return variant;
    }

    private AddOn addOn(String price) {
        AddOn addOn = new AddOn();
        addOn.setId(60L);
        addOn.setName("Extra meat");
        addOn.setPrice(new BigDecimal(price));
        return addOn;
    }

    @Test
    @DisplayName("no configuration sells at the base price — upgrading must not reprice a live partner")
    void noAdjustment_isBasePrice() {
        PartnerPriceResolver resolver = PartnerPriceResolver.of(
                grant(PriceAdjustmentType.NONE, "0", "0"), List.of());

        assertThat(resolver.forProduct(product("30000"))).isEqualByComparingTo("30000");
        assertThat(resolver.isPassThrough()).isTrue();
    }

    @Test
    @DisplayName("a percent markup applies to the base price")
    void percentMarkup() {
        PartnerPriceResolver resolver = PartnerPriceResolver.of(
                grant(PriceAdjustmentType.PERCENT, "15", "0"), List.of());

        assertThat(resolver.forProduct(product("30000"))).isEqualByComparingTo("34500");
    }

    @Test
    @DisplayName("an absolute markup adds a flat amount")
    void amountMarkup() {
        PartnerPriceResolver resolver = PartnerPriceResolver.of(
                grant(PriceAdjustmentType.AMOUNT, "500", "0"), List.of());

        assertThat(resolver.forProduct(product("30000"))).isEqualByComparingTo("30500");
    }

    @Test
    @DisplayName("rounding tidies a markup that lands on an ugly number")
    void roundingToNearestMultiple() {
        // 30000 + 13% = 33900 → nearest 500 is 34000. A partner displays our number verbatim, so this
        // is the difference between a menu reading 34 000 and one reading 33 900.
        PartnerPriceResolver resolver = PartnerPriceResolver.of(
                grant(PriceAdjustmentType.PERCENT, "13", "500"), List.of());

        assertThat(resolver.forProduct(product("30000"))).isEqualByComparingTo("34000");
    }

    @Test
    @DisplayName("rounding goes to the NEAREST multiple, not always up")
    void roundingIsHalfUp_notCeiling() {
        // 30000 + 1% = 30300 → nearest 500 is 30500; 30000 + 0.5% = 30150 → 30000.
        assertThat(PartnerPriceResolver.of(grant(PriceAdjustmentType.PERCENT, "1", "500"), List.of())
                .forProduct(product("30000"))).isEqualByComparingTo("30500");
        assertThat(PartnerPriceResolver.of(grant(PriceAdjustmentType.PERCENT, "0.5", "500"), List.of())
                .forProduct(product("30000"))).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("a product rule beats the venue default")
    void productOverride_beatsDefault() {
        PartnerPriceResolver resolver = PartnerPriceResolver.of(
                grant(PriceAdjustmentType.PERCENT, "15", "0"),
                List.of(rule(PriceRuleScope.PRODUCT, PRODUCT_ID, PriceAdjustmentType.AMOUNT, "1000")));

        // The product rule replaces the default entirely; the two do not stack.
        assertThat(resolver.forProduct(product("30000"))).isEqualByComparingTo("31000");
    }

    @Test
    @DisplayName("a category rule beats the venue default, and a product rule beats the category")
    void resolutionOrder_mostSpecificWins() {
        PartnerPriceResolver categoryOnly = PartnerPriceResolver.of(
                grant(PriceAdjustmentType.PERCENT, "15", "0"),
                List.of(rule(PriceRuleScope.CATEGORY, CATEGORY_ID, PriceAdjustmentType.PERCENT, "5")));
        assertThat(categoryOnly.forProduct(product("30000"))).isEqualByComparingTo("31500");

        PartnerPriceResolver both = PartnerPriceResolver.of(
                grant(PriceAdjustmentType.PERCENT, "15", "0"),
                List.of(rule(PriceRuleScope.CATEGORY, CATEGORY_ID, PriceAdjustmentType.PERCENT, "5"),
                        rule(PriceRuleScope.PRODUCT, PRODUCT_ID, PriceAdjustmentType.PERCENT, "20")));
        assertThat(both.forProduct(product("30000"))).isEqualByComparingTo("36000");
    }

    @Test
    @DisplayName("FIXED replaces the price outright and is not rounded")
    void fixedOverride_isExactAndUnrounded() {
        PartnerPriceResolver resolver = PartnerPriceResolver.of(
                grant(PriceAdjustmentType.PERCENT, "15", "500"),
                List.of(rule(PriceRuleScope.PRODUCT, PRODUCT_ID, PriceAdjustmentType.FIXED, "33333")));

        // Someone who typed an exact price meant that exact price; rounding it to 33 500 would
        // quietly contradict them.
        assertThat(resolver.forProduct(product("30000"))).isEqualByComparingTo("33333");
    }

    @Test
    @DisplayName("a variant inherits its product's rule when it has none of its own")
    void variant_inheritsProductRule() {
        PartnerPriceResolver resolver = PartnerPriceResolver.of(
                grant(PriceAdjustmentType.NONE, "0", "0"),
                List.of(rule(PriceRuleScope.PRODUCT, PRODUCT_ID, PriceAdjustmentType.PERCENT, "10")));

        // "This dish +10%" has to cover its sizes, or someone must write a rule per size and will
        // eventually miss one.
        assertThat(resolver.forVariant(variant("50000"), product("30000"))).isEqualByComparingTo("55000");
    }

    @Test
    @DisplayName("a variant inherits its product's CATEGORY rule too")
    void variant_inheritsCategoryRule() {
        PartnerPriceResolver resolver = PartnerPriceResolver.of(
                grant(PriceAdjustmentType.NONE, "0", "0"),
                List.of(rule(PriceRuleScope.CATEGORY, CATEGORY_ID, PriceAdjustmentType.PERCENT, "10")));

        assertThat(resolver.forVariant(variant("50000"), product("30000"))).isEqualByComparingTo("55000");
    }

    @Test
    @DisplayName("a variant's own rule beats its product's")
    void variantOverride_beatsProduct() {
        PartnerPriceResolver resolver = PartnerPriceResolver.of(
                grant(PriceAdjustmentType.NONE, "0", "0"),
                List.of(rule(PriceRuleScope.PRODUCT, PRODUCT_ID, PriceAdjustmentType.PERCENT, "10"),
                        rule(PriceRuleScope.VARIANT, VARIANT_ID, PriceAdjustmentType.PERCENT, "30")));

        assertThat(resolver.forVariant(variant("50000"), product("30000"))).isEqualByComparingTo("65000");
    }

    @Test
    @DisplayName("add-ons take the venue default, never an item override")
    void addOn_usesVenueDefaultOnly() {
        PartnerPriceResolver resolver = PartnerPriceResolver.of(
                grant(PriceAdjustmentType.PERCENT, "10", "0"),
                // An add-on is shared across products, so a product rule cannot apply to it
                // unambiguously — the same "extra shot" would have two prices.
                List.of(rule(PriceRuleScope.PRODUCT, PRODUCT_ID, PriceAdjustmentType.PERCENT, "50")));

        assertThat(resolver.forAddOn(addOn("12000"))).isEqualByComparingTo("13200");
    }

    @Test
    @DisplayName("a discount is allowed but can never produce a negative price")
    void discount_flooredAtZero() {
        PartnerPriceResolver percent = PartnerPriceResolver.of(
                grant(PriceAdjustmentType.PERCENT, "-100", "0"), List.of());
        assertThat(percent.forProduct(product("30000"))).isEqualByComparingTo("0");

        // An absolute discount larger than the price would otherwise invert it into money we owe them.
        PartnerPriceResolver amount = PartnerPriceResolver.of(
                grant(PriceAdjustmentType.AMOUNT, "-50000", "0"), List.of());
        assertThat(amount.forProduct(product("30000"))).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("a rule for a different product does not leak onto this one")
    void unrelatedRule_isIgnored() {
        PartnerPriceResolver resolver = PartnerPriceResolver.of(
                grant(PriceAdjustmentType.NONE, "0", "0"),
                List.of(rule(PriceRuleScope.PRODUCT, 999L, PriceAdjustmentType.PERCENT, "50")));

        assertThat(resolver.forProduct(product("30000"))).isEqualByComparingTo("30000");
    }

    @Test
    @DisplayName("a product with no category resolves without blowing up")
    void productWithoutCategory_isSafe() {
        Product orphan = new Product();
        orphan.setId(PRODUCT_ID);
        orphan.setPrice(new BigDecimal("30000"));

        PartnerPriceResolver resolver = PartnerPriceResolver.of(
                grant(PriceAdjustmentType.PERCENT, "10", "0"),
                List.of(rule(PriceRuleScope.CATEGORY, CATEGORY_ID, PriceAdjustmentType.PERCENT, "50")));

        assertThat(resolver.forProduct(orphan)).isEqualByComparingTo("33000");
    }

    @Test
    @DisplayName("passThrough() leaves every price alone")
    void passThrough_changesNothing() {
        PartnerPriceResolver resolver = PartnerPriceResolver.passThrough();

        assertThat(resolver.forProduct(product("30000"))).isEqualByComparingTo("30000");
        assertThat(resolver.forAddOn(addOn("12000"))).isEqualByComparingTo("12000");
        assertThat(resolver.isPassThrough()).isTrue();
    }
}
