package com.elcafe.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The shared placeholder substitution that SMS, Telegram, and Instagram templates all render through.
 * These pin the contract the three channels used to implement (subtly) differently.
 */
class MessageTemplateRendererTest {

    @Test
    @DisplayName("substitutes each {key} with its value")
    void substitutesPlaceholders() {
        String out = MessageTemplateRenderer.render(
                "Hi {name}, your order {orderNo} is ready",
                Map.of("name", "Aziz", "orderNo", "ORD-42"));
        assertThat(out).isEqualTo("Hi Aziz, your order ORD-42 is ready");
    }

    @Test
    @DisplayName("a null value renders as empty string, never the literal \"null\"")
    void nullValueBecomesEmpty() {
        Map<String, String> vars = new HashMap<>();
        vars.put("name", null);
        assertThat(MessageTemplateRenderer.render("Hi {name}!", vars)).isEqualTo("Hi !");
    }

    @Test
    @DisplayName("a null placeholder map returns the template unchanged (was an NPE on SMS/Telegram)")
    void nullMapDoesNotThrow() {
        assertThatCode(() -> MessageTemplateRenderer.render("Hi {name}", null))
                .doesNotThrowAnyException();
        assertThat(MessageTemplateRenderer.render("Hi {name}", null)).isEqualTo("Hi {name}");
    }

    @Test
    @DisplayName("an empty map returns the template unchanged")
    void emptyMapReturnsTemplate() {
        assertThat(MessageTemplateRenderer.render("Hi {name}", Map.of())).isEqualTo("Hi {name}");
    }

    @Test
    @DisplayName("a null template is passed through")
    void nullTemplatePassthrough() {
        assertThat(MessageTemplateRenderer.render(null, Map.of("a", "b"))).isNull();
    }

    @Test
    @DisplayName("a placeholder with no matching key is left intact")
    void unknownPlaceholderLeftIntact() {
        assertThat(MessageTemplateRenderer.render("Hi {name}, code {code}", Map.of("name", "Aziz")))
                .isEqualTo("Hi Aziz, code {code}");
    }

    @Test
    @DisplayName("every occurrence of a repeated placeholder is replaced")
    void replacesEveryOccurrence() {
        assertThat(MessageTemplateRenderer.render("{x}-{x}-{x}", Map.of("x", "9")))
                .isEqualTo("9-9-9");
    }

    @Test
    @DisplayName("a value is inserted literally — its own braces are not a fresh placeholder to fill")
    void valueInsertedLiterally() {
        // "x" -> "{y}"; with no "y" key in the map, the inserted "{y}" stays verbatim.
        assertThat(MessageTemplateRenderer.render("{x}", Map.of("x", "{y}"))).isEqualTo("{y}");
    }
}
