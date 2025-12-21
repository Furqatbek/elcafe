package com.elcafe.modules.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "stock-alert")
public class StockAlertConfig {
    private boolean enabled = true;
    private int checkIntervalMinutes = 30;
    private int alertCooldownHours = 4;
}
