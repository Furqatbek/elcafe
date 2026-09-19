package com.elcafe.modules.partner.service;

import com.elcafe.modules.menu.entity.AddOn;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.entity.ProductVariant;
import com.elcafe.modules.partner.entity.PartnerPriceRule;
import com.elcafe.modules.partner.entity.PartnerRestaurant;
import com.elcafe.modules.partner.enums.PriceAdjustmentType;
import com.elcafe.modules.partner.enums.PriceRuleScope;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Works out what one partner pays for one item, from the venue's default markup and any overrides.
 *
 * <p><b>The reason this is a single object rather than two code paths:</b> the menu we publish and the
 * price we charge must agree exactly. A partner displays our number to their customer and sends it back
 * as {@code expectedTotal}; if the menu and the order computed prices separately, any divergence would
 * surface as a rejected order for every customer, with no way to tell whose arithmetic was wrong. Both
 * sides call this, so they cannot drift.
 *
 * <p>Immutable and built once per menu build or per order: all the rules for a partner at a venue are
 * loaded in one query, so pricing 300 products costs one round trip rather than 300.
 *
 * <p>Resolution runs most-specific first — variant, then product, then category, then the venue
 * default, then the base price untouched.
 */
public final class PartnerPriceResolver {

    /** Money is stored at 2dp everywhere in this schema; every resolved price lands on that scale. */
    private static final int MONEY_SCALE = 2;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PriceAdjustmentType defaultType;
    private final BigDecimal defaultValue;
    private final BigDecimal rounding;
    private final Map<Long, PartnerPriceRule> byCategory;
    private final Map<Long, PartnerPriceRule> byProduct;
    private final Map<Long, PartnerPriceRule> byVariant;

    private PartnerPriceResolver(PriceAdjustmentType defaultType, BigDecimal defaultValue,
                                 BigDecimal rounding, List<PartnerPriceRule> rules) {
        this.defaultType = defaultType == null ? PriceAdjustmentType.NONE : defaultType;
        this.defaultValue = defaultValue == null ? BigDecimal.ZERO : defaultValue;
        this.rounding = rounding == null ? BigDecimal.ZERO : rounding;
        this.byCategory = new HashMap<>();
        this.byProduct = new HashMap<>();
        this.byVariant = new HashMap<>();
        for (PartnerPriceRule rule : rules) {
            switch (rule.getScope()) {
                case CATEGORY -> byCategory.put(rule.getTargetId(), rule);
                case PRODUCT -> byProduct.put(rule.getTargetId(), rule);
                case VARIANT -> byVariant.put(rule.getTargetId(), rule);
            }
        }
    }

    static PartnerPriceResolver of(PartnerRestaurant grant, List<PartnerPriceRule> rules) {
        return new PartnerPriceResolver(
                grant.getPriceAdjustmentType(), grant.getPriceAdjustmentValue(),
                grant.getPriceRounding(), rules);
    }

    /** A resolver that changes nothing — the base price, for callers with no partner context. */
    public static PartnerPriceResolver passThrough() {
        return new PartnerPriceResolver(PriceAdjustmentType.NONE, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
    }

    /** True when this partner sells at base price, so callers can skip explaining a markup nobody set. */
    public boolean isPassThrough() {
        return defaultType == PriceAdjustmentType.NONE
                && byCategory.isEmpty() && byProduct.isEmpty() && byVariant.isEmpty();
    }

    public BigDecimal forProduct(Product product) {
        return apply(product.getPrice(), ruleForProduct(product));
    }

    /**
     * A variant's own price, adjusted. Falls back through the variant's product and that product's
     * category before the venue default, so "all drinks +10%" covers a drink's sizes without anyone
     * having to write a rule per size.
     */
    public BigDecimal forVariant(ProductVariant variant, Product product) {
        PartnerPriceRule rule = byVariant.get(variant.getId());
        if (rule == null) {
            rule = ruleForProduct(product);
        }
        return apply(variant.getPrice(), rule);
    }

    /**
     * Add-ons take the venue default only. They are shared across products, so a per-product override
     * could not be resolved unambiguously — an "extra shot" attached to two differently-priced drinks
     * would have two answers. Priced consistently with everything else, just without overrides.
     */
    public BigDecimal forAddOn(AddOn addOn) {
        return apply(addOn.getPrice(), null);
    }

    private PartnerPriceRule ruleForProduct(Product product) {
        PartnerPriceRule rule = byProduct.get(product.getId());
        if (rule != null) {
            return rule;
        }
        return product.getCategory() != null ? byCategory.get(product.getCategory().getId()) : null;
    }

    /**
     * Applies a rule, or the venue default when there is none.
     *
     * <p>FIXED deliberately skips rounding: it is an exact price someone typed, and rounding it would
     * quietly contradict them.
     */
    private BigDecimal apply(BigDecimal basePrice, PartnerPriceRule rule) {
        if (basePrice == null) {
            return null;
        }

        PriceAdjustmentType type = rule != null ? rule.getAdjustmentType() : defaultType;
        BigDecimal value = rule != null ? rule.getAdjustmentValue() : defaultValue;

        if (type == null || type == PriceAdjustmentType.NONE) {
            return scaled(basePrice);
        }
        if (type == PriceAdjustmentType.FIXED) {
            return scaled(floorAtZero(value));
        }

        BigDecimal adjusted = type == PriceAdjustmentType.PERCENT
                ? basePrice.add(basePrice.multiply(value).divide(HUNDRED, 6, RoundingMode.HALF_UP))
                : basePrice.add(value);

        return scaled(round(floorAtZero(adjusted)));
    }

    /** Round to the nearest multiple of {@code rounding}; 0 leaves the number alone. */
    private BigDecimal round(BigDecimal price) {
        if (rounding == null || rounding.signum() <= 0) {
            return price;
        }
        return price.divide(rounding, 0, RoundingMode.HALF_UP).multiply(rounding);
    }

    /** A discount must never invert into a negative price the venue would owe the customer. */
    private BigDecimal floorAtZero(BigDecimal price) {
        return price.signum() < 0 ? BigDecimal.ZERO : price;
    }

    private BigDecimal scaled(BigDecimal price) {
        return price.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
