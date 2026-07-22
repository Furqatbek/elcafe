package com.elcafe.modules.ownerbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "telegram.owner-bot")
public class OwnerBotConfig {
    private boolean enabled = true;
    private String token;
    private String username;
}
