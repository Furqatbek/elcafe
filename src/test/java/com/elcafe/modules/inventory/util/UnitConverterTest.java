package com.elcafe.modules.inventory.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UnitConverterTest {

    private static void assertConverts(String qty, String from, String to, String expected) {
        Optional<BigDecimal> result = UnitConverter.convert(new BigDecimal(qty), from, to);
        assertThat(result).as("%s %s -> %s", qty, from, to).isPresent();
        assertThat(result.get()).usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal(expected));
    }

    @Test
    @DisplayName("ml -> L: the case that wrongly refused orders (460 ml vs 90 L of stock)")
    void millilitresToLitres() {
        assertConverts("460", "ml", "L", "0.46");
        // 460 ml is comfortably inside 90 L — no longer 'insufficient'
        assertThat(new BigDecimal("0.46")).isLessThan(new BigDecimal("90"));
    }

    @Test
    @DisplayName("mass conversions both directions")
    void massConversions() {
        assertConverts("1500", "g", "kg", "1.5");
        assertConverts("0.018", "kg", "g", "18");
        assertConverts("500", "mg", "g", "0.5");
    }

    @Test
    @DisplayName("count conversions incl. dozens, which the ingredient data uses")
    void countConversions() {
        assertConverts("12", "pcs", "dozens", "1");
        assertConverts("2", "dozen", "pcs", "24");
    }

    @Test
    @DisplayName("Cyrillic unit spellings are understood")
    void cyrillicUnits() {
        assertConverts("2", "кг", "g", "2000");
        assertConverts("1000", "мл", "л", "1");
        assertConverts("3", "шт", "pcs", "3");
    }

    @Test
    @DisplayName("identical units pass through untouched, ignoring case/space/trailing dot")
    void identicalUnits() {
        assertConverts("7.25", "kg", "kg", "7.25");
        assertConverts("7.25", " KG ", "kg.", "7.25");
    }

    @Test
    @DisplayName("no conversion is invented for unknown units or across dimensions")
    void unconvertible() {
        // different dimensions — mass vs volume
        assertThat(UnitConverter.convert(BigDecimal.ONE, "kg", "L")).isEmpty();
        // unknown unit (e.g. the ingredient literally named/united oddly)
        assertThat(UnitConverter.convert(BigDecimal.ONE, "portion", "kg")).isEmpty();
        assertThat(UnitConverter.convert(BigDecimal.ONE, "kg", "")).isEmpty();
        assertThat(UnitConverter.convert(BigDecimal.ONE, null, "kg")).isEmpty();
        assertThat(UnitConverter.convert(null, "kg", "g")).isEmpty();
    }

    @Test
    @DisplayName("isConvertible reflects convertibility")
    void isConvertible() {
        assertThat(UnitConverter.isConvertible("ml", "L")).isTrue();
        assertThat(UnitConverter.isConvertible("kg", "L")).isFalse();
    }
}
