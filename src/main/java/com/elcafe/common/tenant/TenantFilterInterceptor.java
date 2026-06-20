package com.elcafe.common.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Phase 0 §3.4 — Hibernate tenant backstop.
 *
 * <p>The edge {@link TenantEnforcementFilter} and the controller guard can only act when a request
 * actually carries a {@code restaurantId} (in its path, query, or body). Endpoints that operate on
 * a surrogate {@code /{id}} alone — a payroll entry, an order, an account — carry no tenant marker,
 * so a tenant admin can reach another tenant's row just by guessing its id. This interceptor closes
 * that whole class of holes at the data layer: for every MVC request it enables the Hibernate
 * {@code restaurantFilter}, which appends {@code restaurant_id = :restaurantId} to every query and
 * lazy-load of a {@code @Filter}-annotated entity. A surrogate-id lookup then simply finds nothing
 * for a foreign tenant.
 *
 * <p>Activation is deliberately narrow:
 * <ul>
 *   <li>Only in {@link TenantEnforcementMode#ENFORCE} — in {@code off}/{@code shadow} it is a
 *       no-op, so the backstop flips on with the same single switch as the rest of Phase 0.</li>
 *   <li>Only when {@link TenantContext} holds a concrete restaurant. A {@code null} tenant
 *       (a SUPER_ADMIN aggregate query, or a not-yet-tenant-bound waiter/consumer token) leaves the
 *       filter disabled so those callers are unaffected.</li>
 *   <li>Only on request threads. Background work (schedulers, async jobs) never populates
 *       {@link TenantContext}, so it is never scoped — exactly what cross-tenant batch jobs such as
 *       payroll auto-pay require.</li>
 * </ul>
 *
 * <p>Relies on {@code spring.jpa.open-in-view=true}: the request's {@link EntityManager} is already
 * bound to the thread by the time {@code preHandle} runs, so enabling the filter there also covers
 * queries issued by {@code @Transactional} service methods that join the same persistence context.
 * Sessions opened independently (e.g. {@code REQUIRES_NEW}) are out of scope for this v1 backstop.
 */
@Component
public class TenantFilterInterceptor implements HandlerInterceptor {

    static final String FILTER_NAME = "restaurantFilter";
    static final String PARAM_NAME = "restaurantId";

    private static final Logger log = LoggerFactory.getLogger(TenantFilterInterceptor.class);

    private final EntityManagerFactory entityManagerFactory;
    private final TenantEnforcementMode mode;

    public TenantFilterInterceptor(
            EntityManagerFactory entityManagerFactory,
            @Value("${app.security.tenant-enforcement.mode:shadow}") String mode) {
        this.entityManagerFactory = entityManagerFactory;
        this.mode = TenantEnforcementMode.from(mode);
        log.info("TenantFilterInterceptor initialised in {} mode", this.mode);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (mode != TenantEnforcementMode.ENFORCE) {
            return true;
        }
        Long tenantId = TenantContext.getRestaurantId();
        if (tenantId == null) {
            // SUPER_ADMIN aggregate query, or a token type not yet tenant-bound — leave unscoped.
            return true;
        }
        try {
            enableFilter(tenantId);
        } catch (Exception e) {
            // Never break request processing over the backstop; the controller guard and edge
            // filter still enforce. A failure to scope is logged loudly for investigation.
            log.error("Failed to enable tenant filter for restaurantId={}; query is NOT tenant-scoped",
                    tenantId, e);
        }
        return true;
    }

    /**
     * Enables the Hibernate {@code restaurantFilter} on the request-bound session. Extracted and
     * package-visible so {@link #preHandle}'s gating logic can be unit-tested without a live
     * persistence context.
     */
    void enableFilter(Long tenantId) {
        EntityManager em = EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory);
        if (em == null) {
            // No session bound to the thread (e.g. open-in-view disabled). Nothing to scope here.
            return;
        }
        em.unwrap(Session.class)
                .enableFilter(FILTER_NAME)
                .setParameter(PARAM_NAME, tenantId);
    }
}
