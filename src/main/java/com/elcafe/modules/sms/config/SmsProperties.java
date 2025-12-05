package com.elcafe.modules.sms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Eskiz.uz SMS broker integration
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "eskiz.sms")
public class SmsProperties {

    /**
     * Base URL for Eskiz.uz API (default: https://notify.eskiz.uz/api)
     */
    private String baseUrl = "https://notify.eskiz.uz/api";

    /**
     * Email for authentication
     */
    private String email;

    /**
     * Password for authentication
     */
    private String password;

    /**
     * Token expiration time in milliseconds (default: 29 days)
     */
    private Long tokenExpirationMs = 29L * 24 * 60 * 60 * 1000;

    /**
     * Connection timeout in milliseconds
     */
    private Integer connectionTimeout = 30000;

    /**
     * Read timeout in milliseconds
     */
    private Integer readTimeout = 30000;

    /**
     * Enable/disable SMS sending (useful for testing)
     */
    private Boolean enabled = true;

    /**
     * Callback URL for delivery reports
     */
    private String callbackUrl;
}
