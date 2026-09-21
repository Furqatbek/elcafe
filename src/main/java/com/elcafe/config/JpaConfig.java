package com.elcafe.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * JPA configuration for auditing support.
 * Provides OffsetDateTime for @CreatedDate and @LastModifiedDate annotations.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "offsetDateTimeProvider")
public class JpaConfig {

    /**
     * Custom DateTimeProvider that returns OffsetDateTime in UTC.
     * This ensures that @CreatedDate and @LastModifiedDate fields
     * receive OffsetDateTime values instead of LocalDateTime.
     */
    @Bean
    public DateTimeProvider offsetDateTimeProvider() {
        return () -> Optional.of(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
