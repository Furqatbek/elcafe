package com.elcafe.modules.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "telegram.bot")
public class TelegramConfig {
    private boolean enabled = true;
    private String token;
    private String username;
}
