package com.elcafe.modules.telegram.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramBotConfigRequest {

    @Size(max = 100, message = "Bot token must be less than 100 characters")
    private String botToken;

    @Size(max = 100, message = "Bot username must be less than 100 characters")
    private String botUsername;

    @Size(max = 500, message = "Webhook URL must be less than 500 characters")
    private String webhookUrl;

    private Boolean isActive;

    private String welcomeMessage;
}
