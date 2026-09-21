package com.elcafe.modules.telegram.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelegramBotConfigResponse {

    private Long id;
    private String botUsername;
    private String webhookUrl;
    private Boolean isActive;
    private String welcomeMessage;
    private Boolean hasToken;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
