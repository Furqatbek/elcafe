package com.elcafe.modules.inventory.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Converts ingredient quantities between units of the same dimension.
 *
 * Recipes ({@code inventory_product_ingredients.unit}) and stock
 * ({@code inventory_ingredients.unit}) are authored independently, so a recipe
 * may ask for 460 ml of something stocked in litres. Without conversion the raw
 * numbers get compared (460 > 90) and the order is wrongly refused for
 * "insufficient stock" while the kitchen has plenty.
 *
 * Returns {@link Optional#empty()} when a conversion cannot be made — either
 * unit is unknown, or they belong to different dimensions (e.g. kg vs L). The
 * caller decides what to do; this class never guesses.
 *
 * Aliases cover the Latin and Cyrillic spellings used across the product data.
 */
public final class UnitConverter {

    private UnitConverter() {
    }

    private enum Dimension { MASS, VOLUME, COUNT }

    private record UnitDef(Dimension dimension, BigDecimal factor) {}

    /** Scale for the division step — generous so small amounts (0.018 kg) survive. */
    private static final int SCALE = 9;

    private static final Map<String, UnitDef> UNITS = new HashMap<>();

    private static void put(Dimension dim, String factor, String... aliases) {
        UnitDef def = new UnitDef(dim, new BigDecimal(factor));
        for (String alias : aliases) {
            UNITS.put(alias, def);
        }
    }

    static {
        // MASS — base unit: gram
        put(Dimension.MASS, "0.001", "mg", "мг");
        put(Dimension.MASS, "1", "g", "gr", "gram", "grams", "г", "гр", "грамм");
        put(Dimension.MASS, "1000", "kg", "kgs", "kilo", "kilogram", "kilograms", "кг", "килограмм");

        // VOLUME — base unit: millilitre
        put(Dimension.VOLUME, "1", "ml", "milliliter", "millilitre", "мл");
        put(Dimension.VOLUME, "10", "cl");
        put(Dimension.VOLUME, "100", "dl");
        put(Dimension.VOLUME, "1000", "l", "lt", "ltr", "litre", "liter", "litres", "liters", "litr", "л", "литр");

        // COUNT — base unit: piece
        put(Dimension.COUNT, "1", "pc", "pcs", "piece", "pieces", "unit", "units", "each", "ea",
                "шт", "штук", "штука", "dona", "дона", "ta");
        put(Dimension.COUNT, "12", "dozen", "dozens", "doz", "дюжина");
    }

    /**
     * Convert {@code quantity} from {@code fromUnit} into {@code toUnit}.
     *
     * @return the converted quantity, or empty when the units are unknown or of
     *         different dimensions (no conversion is invented).
     */
    public static Optional<BigDecimal> convert(BigDecimal quantity, String fromUnit, String toUnit) {
        if (quantity == null) {
            return Optional.empty();
        }
        String from = normalize(fromUnit);
        String to = normalize(toUnit);
        if (from == null || to == null) {
            return Optional.empty();
        }
        if (from.equals(to)) {
            return Optional.of(quantity);
        }

        UnitDef fromDef = UNITS.get(from);
        UnitDef toDef = UNITS.get(to);
        if (fromDef == null || toDef == null || fromDef.dimension() != toDef.dimension()) {
            return Optional.empty();
        }

        return Optional.of(
                quantity.multiply(fromDef.factor())
                        .divide(toDef.factor(), SCALE, RoundingMode.HALF_UP)
                        .stripTrailingZeros());
    }

    /** True when both units are known and share a dimension. */
    public static boolean isConvertible(String fromUnit, String toUnit) {
        return convert(BigDecimal.ONE, fromUnit, toUnit).isPresent();
    }

    private static String normalize(String unit) {
        if (unit == null) {
            return null;
        }
        String trimmed = unit.trim().toLowerCase(Locale.ROOT);
        // tolerate "kg." / "шт."
        while (trimmed.endsWith(".")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed.isEmpty() ? null : trimmed;
    }
}
