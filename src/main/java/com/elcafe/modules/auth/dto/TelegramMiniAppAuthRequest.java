package com.elcafe.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Login request from a Telegram Mini App. The client sends the restaurant it was opened for plus the raw
 * {@code Telegram.WebApp.initData}; the server verifies that {@code initData} was signed by <em>that</em>
 * restaurant's bot token, so neither the restaurant nor the Telegram identity is client-asserted.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramMiniAppAuthRequest {

    @NotNull(message = "Restaurant ID is required")
    @JsonProperty("restaurant_id")
    private Long restaurantId;

    /** Raw, still-URL-encoded {@code Telegram.WebApp.initData}. Verified server-side; never trusted as-is. */
    @NotBlank(message = "initData is required")
    @JsonProperty("init_data")
    private String initData;
}
