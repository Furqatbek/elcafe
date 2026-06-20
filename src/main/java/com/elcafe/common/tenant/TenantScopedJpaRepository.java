package com.elcafe.common.tenant;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Phase 0 §3.4 — the systemic half of the cross-tenant {@code findById} fix.
 *
 * <p>Spring Data's default {@code findById} delegates to {@link EntityManager#find}, a primary-key
 * load that Hibernate's {@code restaurantFilter} does <strong>not</strong> scope (filters apply to
 * queries and association loads, not PK lookups). So a {@code findById(foreignId)} would still
 * return another tenant's row even in {@code enforce} mode — the gap proven by
 * {@code TenantBackstopIsolationTest}. This base class overrides {@code findById} to issue a
 * Criteria query instead, which the filter <em>does</em> scope, so a foreign id returns
 * {@link Optional#empty()} (the controller's {@code orElseThrow} then 404s) without per-endpoint
 * code.
 *
 * <p>Behaviour is unchanged outside {@code enforce}: the filter is only enabled then, so otherwise
 * the query returns exactly what {@code find} would. The trade-offs vs {@code find} are: it always
 * hits the DB (no first-level-cache PK shortcut) and a not-yet-flushed new entity in the same
 * transaction is not visible — neither matters for the read/lookup paths {@code findById} guards.
 * Composite-id entities fall back to the default behaviour (none here are tenant-keyed).
 */
public class TenantScopedJpaRepository<T, ID> extends SimpleJpaRepository<T, ID> {

    private final EntityManager entityManager;
    private final JpaEntityInformation<T, ?> entityInformation;

    public TenantScopedJpaRepository(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {
        super(entityInformation, entityManager);
        this.entityInformation = entityInformation;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<T> findById(ID id) {
        if (id == null) {
            return Optional.empty();
        }
        // Composite keys can't be expressed as a single-attribute equality here; they are never
        // tenant-id-keyed in this codebase, so defer to the default PK load.
        if (entityInformation.hasCompositeId() || entityInformation.getIdAttribute() == null) {
            return super.findById(id);
        }

        Class<T> domainClass = getDomainClass();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> cq = cb.createQuery(domainClass);
        Root<T> root = cq.from(domainClass);
        String idName = entityInformation.getIdAttribute().getName();
        cq.select(root).where(cb.equal(root.get(idName), id));

        List<T> results = entityManager.createQuery(cq).setMaxResults(1).getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }
}
