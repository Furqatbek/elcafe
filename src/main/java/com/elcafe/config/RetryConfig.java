package com.elcafe.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

/**
 * Configuration to enable Spring Retry for resilient operations.
 * Enables @Retryable and @Recover annotations throughout the application.
 */
@Configuration
@EnableRetry
public class RetryConfig {
    // Spring Retry is configured via annotations on individual methods
}
