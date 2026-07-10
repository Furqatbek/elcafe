package com.elcafe.common.tenant;

import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Phase 0 §3.4 follow-up — transaction-scoped Hibernate tenant backstop.
 *
 * <p>{@link TenantFilterInterceptor} enables the {@code restaurantFilter} on the request's
 * open-in-view session, which leaves two gaps its own javadoc concedes: sessions opened
 * independently of the request session ({@code REQUIRES_NEW}) are never scoped, and the whole
 * mechanism dies the day {@code open-in-view} is turned off. This subclass closes both by enabling
 * the filter at the one choke point every data access passes through: {@code doBegin} of the JPA
 * transaction manager. Spring Data repository methods are transactional by definition (reads run in
 * {@code @Transactional(readOnly = true)} from {@code SimpleJpaRepository}), so every repository
 * call — request-driven, {@code REQUIRES_NEW}, or otherwise — begins a transaction through here.
 *
 * <p>Activation matches the interceptor exactly: only in ENFORCE mode, only when
 * {@link TenantContext} carries a concrete restaurant (background threads never populate it, so
 * schedulers/async batch jobs stay unscoped), and never allowed to break the transaction — a
 * failure to scope logs loudly and proceeds, because the edge filter and controller guard still
 * enforce above the data layer.
 *
 * <p>With open-in-view still on, both mechanisms enable the filter on the same session (the
 * transaction joins the request's persistence context) — harmless. With open-in-view off, this one
 * carries the backstop alone.
 */
public class TenantAwareJpaTransactionManager extends JpaTransactionManager {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareJpaTransactionManager.class);

    private final transient TenantEnforcementMode mode;

    public TenantAwareJpaTransactionManager(TenantEnforcementMode mode) {
        this.mode = mode;
        log.info("TenantAwareJpaTransactionManager initialised in {} mode", mode);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);

        if (mode != TenantEnforcementMode.ENFORCE) {
            return;
        }
        Long tenantId = TenantContext.getRestaurantId();
        if (tenantId == null) {
            // SUPER_ADMIN aggregate query, a not-yet-tenant-bound token, or a background thread —
            // leave unscoped, mirroring TenantFilterInterceptor.
            return;
        }
        try {
            // super.doBegin has bound the transaction's EntityManagerHolder by the time it returns
            // (for a pre-existing open-in-view session it reuses that holder — same session, so
            // enabling here and in the interceptor is idempotent).
            EntityManagerHolder holder = (EntityManagerHolder)
                    TransactionSynchronizationManager.getResource(obtainEntityManagerFactory());
            if (holder != null) {
                holder.getEntityManager().unwrap(Session.class)
                        .enableFilter(TenantFilterInterceptor.FILTER_NAME)
                        .setParameter(TenantFilterInterceptor.PARAM_NAME, tenantId);
            }
        } catch (Exception e) {
            log.error("Failed to enable tenant filter on transaction begin for restaurantId={}; "
                    + "queries in this transaction are NOT tenant-scoped at the data layer", tenantId, e);
        }
    }
}
