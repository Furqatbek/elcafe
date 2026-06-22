package com.elcafe.modules.billing.config;

import com.elcafe.modules.billing.interceptor.PlanFeatureGuardInterceptor;
import com.elcafe.modules.billing.interceptor.PlanWriteGuardInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the billing interceptors: {@link PlanWriteGuardInterceptor} (read-only mode, A5) and
 * {@link PlanFeatureGuardInterceptor} (paid-module gating, A4b). Both are inert until a restaurant has
 * a lapsed/limited plan, so registering them unconditionally is safe.
 */
@Configuration
@RequiredArgsConstructor
public class BillingWebMvcConfig implements WebMvcConfigurer {

    private final PlanWriteGuardInterceptor planWriteGuardInterceptor;
    private final PlanFeatureGuardInterceptor planFeatureGuardInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(planWriteGuardInterceptor);
        registry.addInterceptor(planFeatureGuardInterceptor);
    }
}
