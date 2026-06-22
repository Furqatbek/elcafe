package com.elcafe.modules.billing.config;

import com.elcafe.modules.billing.interceptor.PlanWriteGuardInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link PlanWriteGuardInterceptor} so read-only mode (mini-phase A5) is enforced on every
 * MVC request. The interceptor is inert until a restaurant has a lapsed plan, so registering it
 * unconditionally is safe.
 */
@Configuration
@RequiredArgsConstructor
public class BillingWebMvcConfig implements WebMvcConfigurer {

    private final PlanWriteGuardInterceptor planWriteGuardInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(planWriteGuardInterceptor);
    }
}
