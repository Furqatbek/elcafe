package com.elcafe.common.tenant;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Replaces Boot's auto-configured {@code JpaTransactionManager} with
 * {@link TenantAwareJpaTransactionManager} (Boot backs off via {@code @ConditionalOnMissingBean}).
 * The bean keeps the default name so every {@code @Transactional} and {@code TransactionTemplate}
 * in the app — and Spring Data's repository transactions — runs through the tenant-aware manager.
 */
@Configuration
public class TenantTransactionConfig {

    @Bean
    public PlatformTransactionManager transactionManager(
            EntityManagerFactory entityManagerFactory,
            @Value("${app.security.tenant-enforcement.mode:shadow}") String mode) {
        TenantAwareJpaTransactionManager txManager =
                new TenantAwareJpaTransactionManager(TenantEnforcementMode.from(mode));
        txManager.setEntityManagerFactory(entityManagerFactory);
        return txManager;
    }
}
