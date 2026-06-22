package com.elcafe;

import com.elcafe.modules.billing.config.BillingWebMvcConfig;
import com.elcafe.modules.billing.interceptor.PlanFeatureGuardInterceptor;
import com.elcafe.modules.billing.interceptor.PlanWriteGuardInterceptor;
import com.elcafe.modules.billing.scheduler.PlanExpiryNotifier;
import com.elcafe.modules.billing.service.PlanGateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test: boots the ENTIRE Spring application context against the H2 'test' profile.
 *
 * <p>This is the only {@code @SpringBootTest} in the suite — every other test is a Mockito unit test,
 * a {@code @DataJpaTest} slice, or a standalone MockMvc setup, none of which start the real context.
 * So this is the single guard that catches whole-application wiring regressions a slice can never see:
 * bean conflicts, missing dependencies, a broken {@code @Configuration}, or a {@code @PostConstruct}
 * that fails on boot.
 */
@SpringBootTest
@ActiveProfiles("test")
// The full context uses the real datasource (H2 with DATABASE_TO_UPPER=FALSE), where the
// hibernate.default_schema=public from application.yml is a lowercase schema H2 doesn't ship. Postgres
// always has a 'public' schema; create it at connection time (INIT) so it exists before Hibernate's
// create-drop runs any DDL — otherwise the drop phase logs a stack trace per table. (@DataJpaTest
// slices dodge this entirely because @AutoConfigureTestDatabase swaps in a default-mode H2.)
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:smoketestdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;"
                + "DB_CLOSE_ON_EXIT=FALSE;DATABASE_TO_UPPER=FALSE;NON_KEYWORDS=VALUE;"
                + "INIT=CREATE SCHEMA IF NOT EXISTS public")
class ApplicationContextSmokeTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    void billingGatingIsWired() {
        // The plan gate, both guard interceptors, the MVC registrar that installs them, and the expiry
        // scheduler must all be live beans — this is the Phase 1 wiring, end to end. If any drops out
        // of the context (e.g. a removed @Component), the whole gating story silently breaks.
        assertThat(context.getBean(PlanGateService.class)).isNotNull();
        assertThat(context.getBean(PlanWriteGuardInterceptor.class)).isNotNull();
        assertThat(context.getBean(PlanFeatureGuardInterceptor.class)).isNotNull();
        assertThat(context.getBean(BillingWebMvcConfig.class)).isNotNull();
        assertThat(context.getBean(PlanExpiryNotifier.class)).isNotNull();
    }
}
