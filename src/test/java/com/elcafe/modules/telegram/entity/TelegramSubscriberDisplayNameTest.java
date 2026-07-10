package com.elcafe.modules.telegram.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the display-name preference order after the FUNC fix: the wizard-collected {@code display_name}
 * used to be stored but never returned (the hand-written getter shadowed the field with derived names
 * only), so greetings/campaigns ignored the name users typed during registration.
 */
class TelegramSubscriberDisplayNameTest {

    private TelegramSubscriber subscriber(String displayName, String firstName, String lastName, String username) {
        return TelegramSubscriber.builder()
                .telegramUserId(777L)
                .displayName(displayName)
                .firstName(firstName)
                .lastName(lastName)
                .username(username)
                .build();
    }

    @Test
    @DisplayName("wizard-entered name wins over Telegram profile names")
    void storedDisplayNameWins() {
        assertThat(subscriber("Alisher Usmanov", "Ali", "U", "aliu").getDisplayName())
                .isEqualTo("Alisher Usmanov");
    }

    @Test
    @DisplayName("blank stored name falls back to the derived chain")
    void blankFallsBack() {
        assertThat(subscriber("   ", "Ali", "U", "aliu").getDisplayName()).isEqualTo("Ali U");
        assertThat(subscriber(null, "Ali", null, "aliu").getDisplayName()).isEqualTo("Ali");
        assertThat(subscriber(null, null, null, "aliu").getDisplayName()).isEqualTo("@aliu");
        assertThat(subscriber(null, null, null, null).getDisplayName()).isEqualTo("User 777");
    }
}
