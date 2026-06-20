package com.elcafe.common.tenant;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link TenantInsertGuard} as the shared SessionFactory interceptor so it vetoes
 * cross-tenant inserts (Phase 0 §3.4 write-side). The guard is a no-op outside {@code enforce}
 * mode, so registering it unconditionally is safe.
 */
@Configuration
public class TenantPersistenceConfig {

    @Bean
    public HibernatePropertiesCustomizer tenantInsertGuardCustomizer(TenantInsertGuard guard) {
        return props -> props.put(AvailableSettings.INTERCEPTOR, guard);
    }
}
