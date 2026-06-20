package com.elcafe.common.tenant;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link TenantFilterInterceptor} so the Hibernate tenant backstop (Phase 0 §3.4) runs on
 * every MVC request. The interceptor is a no-op outside {@code enforce} mode, so registering it
 * unconditionally is safe.
 */
@Configuration
@RequiredArgsConstructor
public class TenantWebMvcConfig implements WebMvcConfigurer {

    private final TenantFilterInterceptor tenantFilterInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantFilterInterceptor);
    }
}
