package com.elcafe.common.tenant;

import com.elcafe.modules.restaurant.entity.Restaurant;
import org.hibernate.Interceptor;
import org.hibernate.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Phase 0 §3.4 — the write-side bookend to the {@code restaurantFilter}.
 *
 * <p>The Hibernate filter scopes reads; the {@link TenantScopedJpaRepository} closes {@code findById}
 * reads. Neither touches INSERTs, so a caller could still create a row under another tenant by putting
 * a foreign {@code restaurantId} in the request body (which the edge {@code TenantEnforcementFilter}
 * cannot see). This shared SessionFactory interceptor vetoes any insert whose {@code restaurant_id}
 * differs from the request's bound tenant.
 *
 * <p>Gating mirrors the read filter exactly, so it is safe-by-construction: it acts only in
 * {@code enforce} mode and only when {@link TenantContext} holds a concrete tenant. A {@code null}
 * tenant (SUPER_ADMIN cross-tenant work, background jobs) is never checked, and shadow/off are no-ops.
 * It recognises both tenant mappings used in this codebase: a plain {@code Long restaurantId} column
 * and a {@code @ManyToOne Restaurant} association.
 */
@Component
public class TenantInsertGuard implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantInsertGuard.class);

    private final TenantEnforcementMode mode;

    public TenantInsertGuard(@Value("${app.security.tenant-enforcement.mode:shadow}") String mode) {
        this.mode = TenantEnforcementMode.from(mode);
    }

    @Override
    public boolean onSave(Object entity, Object id, Object[] state, String[] propertyNames, Type[] types) {
        return guardInsert(entity, state, propertyNames);
    }

    /** Deprecated {@code Serializable}-id overload — overridden too so the guard fires regardless of
     *  which {@code onSave} variant Hibernate dispatches. */
    @Override
    @SuppressWarnings("deprecation")
    public boolean onSave(Object entity, java.io.Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        return guardInsert(entity, state, propertyNames);
    }

    boolean guardInsert(Object entity, Object[] state, String[] propertyNames) {
        if (mode != TenantEnforcementMode.ENFORCE) {
            return false;
        }
        Long tenant = TenantContext.getRestaurantId();
        if (tenant == null) {
            // Unscoped caller (SUPER_ADMIN aggregate / background job) — not tenant-checked.
            return false;
        }
        Long target = restaurantIdOf(state, propertyNames);
        if (target != null && !target.equals(tenant)) {
            log.warn("[tenant-enforce] BLOCKED cross-tenant insert of {} restaurant_id={} by tenant={}",
                    entity.getClass().getSimpleName(), target, tenant);
            throw new CrossTenantWriteException(entity.getClass().getSimpleName(), target, tenant);
        }
        return false;
    }

    /** Extracts the row's restaurant id from either a {@code restaurantId} Long or a {@code restaurant} association. */
    private Long restaurantIdOf(Object[] state, String[] propertyNames) {
        for (int i = 0; i < propertyNames.length; i++) {
            String name = propertyNames[i];
            Object value = state[i];
            if (value == null) {
                continue;
            }
            if ("restaurantId".equals(name) && value instanceof Long l) {
                return l;
            }
            if ("restaurant".equals(name) && value instanceof Restaurant r) {
                return r.getId();
            }
        }
        return null;
    }

    /** Thrown when an insert targets a restaurant other than the request's bound tenant. */
    public static class CrossTenantWriteException extends RuntimeException {
        public CrossTenantWriteException(String entity, Long target, Long tenant) {
            super("Cross-tenant write blocked: " + entity + " targets restaurant " + target
                    + " but the caller is bound to restaurant " + tenant);
        }
    }
}
