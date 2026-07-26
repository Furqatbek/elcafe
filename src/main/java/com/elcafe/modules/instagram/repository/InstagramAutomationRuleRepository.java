package com.elcafe.modules.instagram.repository;

import com.elcafe.modules.instagram.entity.InstagramAutomationRule;
import com.elcafe.modules.instagram.enums.InstagramTriggerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Mirrors {@code TelegramAutomationRuleRepository}, adapted to Instagram's per-tenant model: every
 * CRUD-surface finder is explicitly tenant-scoped, since a rule belongs to exactly one restaurant's own
 * library (matching {@code InstagramTemplateRepository}). {@link #findActiveRulesWithTemplate} is the
 * one deliberate exception — see its own javadoc.
 */
@Repository
public interface InstagramAutomationRuleRepository extends JpaRepository<InstagramAutomationRule, Long> {

    /** Tenant-scoped by-id lookup — closes the IDOR a bare {@code findById} leaves open. */
    Optional<InstagramAutomationRule> findByIdAndRestaurantId(Long id, Long restaurantId);

    /** One tenant's rules, newest first. */
    Page<InstagramAutomationRule> findByRestaurantIdOrderByIdDesc(Long restaurantId, Pageable pageable);

    /** Tenant-scoped uniqueness check backing the UNIQUE(restaurant_id, name) constraint. */
    boolean existsByRestaurantIdAndName(Long restaurantId, String name);

    /**
     * Every ACTIVE rule of one trigger type, across EVERY tenant, with its template eagerly fetched —
     * {@code InstagramScheduler}'s entry point for both the daily BIRTHDAY and WIN_BACK sweeps.
     *
     * <p>Deliberately NOT restaurant-scoped, mirroring {@code TelegramAutomationRuleRepository
     * #findActiveRulesWithTemplate}/{@code #findByTriggerTypeAndIsActiveTrue}: the scheduler runs on a
     * timer with no authenticated request and no single tenant to scope to — it processes every
     * restaurant's rules in one sweep. Tenant isolation for everything downstream of a rule (which
     * subscribers get read, which bot config sends) instead comes from the scheduler explicitly using
     * {@code rule.getRestaurantId()} on every subsequent query, exactly like
     * {@code InstagramCampaignExecutor} resolves its tenant from the campaign row it already loaded
     * rather than from an ambient filter. The {@code LEFT JOIN FETCH} avoids a
     * LazyInitializationException on {@code rule.getTemplate()} after this method's own (read-only,
     * Spring-Data-managed) transaction has closed.
     */
    @Query("SELECT r FROM InstagramAutomationRule r LEFT JOIN FETCH r.template " +
           "WHERE r.triggerType = :triggerType AND r.isActive = true")
    List<InstagramAutomationRule> findActiveRulesWithTemplate(@Param("triggerType") InstagramTriggerType triggerType);
}
