package com.elcafe.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a Telegram Mini App login.
 *
 * <p>Two shapes, distinguished by {@link #registrationRequired}:
 * <ul>
 *   <li>{@code registrationRequired = false} → {@link #auth} carries the same consumer tokens the OTP
 *       flow issues; the Mini App proceeds straight to the menu.</li>
 *   <li>{@code registrationRequired = true} → the Telegram identity is verified but no phone-keyed
 *       customer exists yet (the visitor never shared their contact in the bot). {@link #auth} is null;
 *       the Mini App prompts them to finish the one-tap "Share contact" step in the bot.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramMiniAppAuthResponse {

    @JsonProperty("registration_required")
    private boolean registrationRequired;

    /** Consumer session tokens; null when {@link #registrationRequired} is true. */
    private ConsumerAuthResponse auth;

    /** Known contact + default saved delivery pin, so the Mini App can prefill checkout. */
    private Prefill prefill;

    public static TelegramMiniAppAuthResponse authenticated(ConsumerAuthResponse auth, Prefill prefill) {
        return TelegramMiniAppAuthResponse.builder()
                .registrationRequired(false)
                .auth(auth)
                .prefill(prefill)
                .build();
    }

    public static TelegramMiniAppAuthResponse registrationRequired() {
        return TelegramMiniAppAuthResponse.builder()
                .registrationRequired(true)
                .auth(null)
                .build();
    }

    /**
     * Checkout prefill for the Mini App. The delivery pin comes from the customer's default
     * Telegram-shared location (if any); latitude/longitude are null when none is saved.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Prefill {
        @JsonProperty("first_name")
        private String firstName;
        @JsonProperty("last_name")
        private String lastName;
        private String phone;
        private Double latitude;
        private Double longitude;
    }
}
