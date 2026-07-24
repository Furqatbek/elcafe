package com.elcafe.modules.billing.service;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.common.audit.entity.AuditAction;
import com.elcafe.common.audit.service.AuditService;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.ForbiddenException;
import com.elcafe.modules.billing.dto.BillingStatusDto;
import com.elcafe.modules.billing.dto.PlanSummaryDto;
import com.elcafe.modules.billing.dto.SetPlanRequest;
import com.elcafe.modules.billing.entity.SubscriptionPlan;
import com.elcafe.modules.billing.repository.SubscriptionPlanRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single source of truth for a restaurant's plan: read its current tier + feature set, compute
 * expiry/grace/read-only state, gate paid modules, and (admin) change the plan.
 *
 * <p>Reads are served from a small in-process TTL cache so the per-request gate checks don't hit the
 * DB every time; the cache is invalidated on plan change. Gating is live: {@code PlanFeatureGuardInterceptor}
 * calls {@link #requireFeatureIfPlanned(Long, String)} for paid API paths, which throws
 * {@link com.elcafe.exception.ForbiddenException}{@code ("plan.feature_required:<code>")} (HTTP 403)
 * when the restaurant's plan lacks the feature.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanGateService {

    /** Days of full access after expiry before read-only mode kicks in. */
    public static final int GRACE_DAYS = 3;
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final SubscriptionPlanRepository planRepository;
    private final RestaurantRepository restaurantRepository;
    private final RestaurantAuthorizationService authorizationService;
    private final AuditService auditService;

    private final Map<Long, CachedPlan> cache = new ConcurrentHashMap<>();

    /** Immutable snapshot of a restaurant's plan state, copied out of the entity under a session. */
    public record PlanSnapshot(Long restaurantId, String planCode, String planName,
                               Set<String> featureCodes, LocalDateTime planExpiresAt, boolean trial) {}

    private record CachedPlan(PlanSnapshot snapshot, Instant loadedAt) {}

    // ---------------------------------------------------------------- reads + cache

    @Transactional(readOnly = true)
    public PlanSnapshot getCurrentPlan(Long restaurantId) {
        if (restaurantId == null) {
            return null;
        }
        CachedPlan cached = cache.get(restaurantId);
        if (cached != null && Duration.between(cached.loadedAt(), Instant.now()).compareTo(CACHE_TTL) < 0) {
            return cached.snapshot();
        }
        PlanSnapshot snapshot = load(restaurantId);
        if (snapshot != null) {
            cache.put(restaurantId, new CachedPlan(snapshot, Instant.now()));
        }
        return snapshot;
    }

    private PlanSnapshot load(Long restaurantId) {
        // Must not rely on an ambient session: the gate interceptors reach here through unannotated
        // entry points (requireWriteAccess/requireFeatureIfPlanned are self-invocations as far as
        // @Transactional is concerned), and open-in-view is off. The fetch-joined finder returns the
        // plan + feature codes fully initialised from the repository's own transaction; plain
        // findById handed back lazy proxies that blew up with LazyInitializationException on every
        // gated request of a planned tenant (caught by the post-OSIV-flip boot smoke).
        Restaurant restaurant = restaurantRepository.findByIdWithPlanFeatures(restaurantId).orElse(null);
        if (restaurant == null || restaurant.getPlan() == null) {
            return null;
        }
        SubscriptionPlan plan = restaurant.getPlan();
        Set<String> features = plan.getFeatureCodes() == null
                ? Set.of() : new LinkedHashSet<>(plan.getFeatureCodes());
        return new PlanSnapshot(restaurantId, plan.getCode(), plan.getName(), features,
                restaurant.getPlanExpiresAt(), Boolean.TRUE.equals(restaurant.getIsTrial()));
    }

    public void invalidate(Long restaurantId) {
        if (restaurantId != null) {
            cache.remove(restaurantId);
        }
    }

    // ---------------------------------------------------------------- feature gating

    public boolean hasFeature(Long restaurantId, String featureCode) {
        PlanSnapshot snapshot = getCurrentPlan(restaurantId);
        return snapshot != null && snapshot.featureCodes().contains(featureCode);
    }

    /**
     * The boolean twin of {@link #requireFeatureIfPlanned}: {@code true} when the restaurant has no
     * plan at all (tests / unseeded setups must not gate), otherwise whether the plan carries the
     * feature. For callers that filter or branch rather than throw — e.g. the public reservable list.
     */
    public boolean hasFeatureIfPlanned(Long restaurantId, String featureCode) {
        PlanSnapshot snapshot = getCurrentPlan(restaurantId);
        return snapshot == null || snapshot.featureCodes().contains(featureCode);
    }

    /** Gate a paid module for the current request's restaurant. */
    public void requireFeature(String featureCode) {
        requireFeature(authorizationService.getCurrentUserRestaurantId(), featureCode);
    }

    public void requireFeature(Long restaurantId, String featureCode) {
        if (!hasFeature(restaurantId, featureCode)) {
            // Structured message so the frontend can render an upgrade CTA instead of a generic 403.
            throw new ForbiddenException("plan.feature_required:" + featureCode);
        }
    }

    /**
     * Like {@link #requireFeature(Long, String)} but a no-op when the restaurant has no plan at all.
     * Production restaurants always have one (plan_id is NOT NULL since V156), so this gates as
     * expected there; a null plan only arises in tests / unseeded edge cases, where gating must not
     * fire. Used by the path-based {@code PlanFeatureGuardInterceptor} (mini-phase A4b).
     */
    public void requireFeatureIfPlanned(Long restaurantId, String featureCode) {
        PlanSnapshot snapshot = getCurrentPlan(restaurantId);
        if (snapshot != null && !snapshot.featureCodes().contains(featureCode)) {
            throw new ForbiddenException("plan.feature_required:" + featureCode);
        }
    }

    // ---------------------------------------------------------------- expiry / read-only

    /** Past expiry + grace window — the restaurant can read but not create/accept new work. */
    public boolean isReadOnly(Long restaurantId) {
        PlanSnapshot snapshot = getCurrentPlan(restaurantId);
        return snapshot != null && isReadOnly(snapshot.planExpiresAt(), LocalDateTime.now());
    }

    private boolean isReadOnly(LocalDateTime expiresAt, LocalDateTime now) {
        return expiresAt != null && now.isAfter(expiresAt.plusDays(GRACE_DAYS));
    }

    /** Blocks state-changing flows once a plan has fully lapsed (wired up in mini-phase A5). */
    public void requireWriteAccess(Long restaurantId) {
        if (isReadOnly(restaurantId)) {
            throw new ForbiddenException("plan.read_only");
        }
    }

    // ---------------------------------------------------------------- API surface

    @Transactional(readOnly = true)
    public BillingStatusDto getBillingStatus(Long restaurantId) {
        PlanSnapshot snapshot = getCurrentPlan(restaurantId);
        if (snapshot == null) {
            return BillingStatusDto.builder().restaurantId(restaurantId).build();
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = snapshot.planExpiresAt();

        Long daysUntilExpiry = expiresAt == null
                ? null : ChronoUnit.DAYS.between(now.toLocalDate(), expiresAt.toLocalDate());
        boolean inGrace = expiresAt != null
                && now.isAfter(expiresAt) && !now.isAfter(expiresAt.plusDays(GRACE_DAYS));
        boolean readOnly = isReadOnly(expiresAt, now);

        return BillingStatusDto.builder()
                .restaurantId(restaurantId)
                .planCode(snapshot.planCode())
                .planName(snapshot.planName())
                .featureCodes(new ArrayList<>(snapshot.featureCodes()))
                .planExpiresAt(expiresAt)
                .isTrial(snapshot.trial())
                .daysUntilExpiry(daysUntilExpiry)
                .inGracePeriod(inGrace)
                .readOnly(readOnly)
                .build();
    }

    @Transactional(readOnly = true)
    public List<PlanSummaryDto> listActivePlans() {
        return planRepository.findAllByActiveTrueOrderBySortOrderAsc().stream()
                .map(plan -> PlanSummaryDto.builder()
                        .code(plan.getCode())
                        .name(plan.getName())
                        .monthlyPrice(plan.getMonthlyPrice())
                        .featureCodes(plan.getFeatureCodes() == null
                                ? List.of() : new ArrayList<>(plan.getFeatureCodes()))
                        .sortOrder(plan.getSortOrder())
                        .build())
                .toList();
    }

    /** Admin-only: point a restaurant at a different plan. Audited, cache invalidated. */
    @Transactional
    public BillingStatusDto setPlan(SetPlanRequest request, UserPrincipal actor) {
        Restaurant restaurant = restaurantRepository.findById(request.getRestaurantId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Restaurant not found: " + request.getRestaurantId()));
        SubscriptionPlan plan = planRepository.findByCode(request.getPlanCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown plan code: " + request.getPlanCode()));

        String previousCode = restaurant.getPlan() != null ? restaurant.getPlan().getCode() : null;

        restaurant.setPlan(plan);
        restaurant.setPlanStartedAt(LocalDateTime.now());
        restaurant.setPlanExpiresAt(request.getPlanExpiresAt());
        restaurant.setIsTrial(Boolean.TRUE.equals(request.getIsTrial()));
        restaurantRepository.save(restaurant);
        invalidate(restaurant.getId());

        auditService.logAction(AuditService.AuditLogBuilder.create()
                .action(AuditAction.PLAN_CHANGED)
                .entityType("Restaurant")
                .entityId(restaurant.getId())
                .restaurantId(restaurant.getId())
                .userId(actor != null ? actor.getId() : null)
                .username(actor != null ? actor.getUsername() : "system")
                .userRole(actor != null && actor.getRole() != null ? actor.getRole().name() : null)
                .previousValue(previousCode)
                .newValue(plan.getCode())
                .actionDetail("Subscription plan changed via admin set-plan"));

        log.info("Plan changed for restaurant {}: {} -> {} (expires {}, trial {})",
                restaurant.getId(), previousCode, plan.getCode(),
                request.getPlanExpiresAt(), Boolean.TRUE.equals(request.getIsTrial()));

        return getBillingStatus(restaurant.getId());
    }
}
