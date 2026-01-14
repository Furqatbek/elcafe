package com.elcafe.modules.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "financial-alert")
public class FinancialAlertConfig {

    /**
     * Enable or disable financial alerts
     */
    private boolean enabled = true;

    /**
     * Check interval in minutes for sending daily reports
     */
    private int checkIntervalMinutes = 15;

    /**
     * Default report time (HH:mm format)
     */
    private String defaultReportTime = "23:00";
}
