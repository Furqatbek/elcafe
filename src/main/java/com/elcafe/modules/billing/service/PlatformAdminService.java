package com.elcafe.modules.billing.service;

import com.elcafe.common.audit.entity.AuditAction;
import com.elcafe.common.audit.service.AuditService;
import com.elcafe.modules.billing.dto.BillingStatusDto;
import com.elcafe.modules.billing.dto.ChangePlanRequest;
import com.elcafe.modules.billing.dto.SetPlanRequest;
import com.elcafe.modules.billing.dto.TenantSummaryDto;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * Cross-tenant subscription administration for the SUPER_ADMIN platform console. Where
 * {@link PlanGateService} serves the per-request, tenant-scoped gate, every method here operates on an
 * arbitrary restaurant by id. All mutations are audited; plan changes reuse
 * {@link PlanGateService#setPlan} so the audit + cache-invalidation behaviour is identical to the
 * per-tenant admin path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformAdminService {

    private final RestaurantRepository restaurantRepository;
    private final PlanGateService planGateService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public Page<TenantSummaryDto> listTenants(String search, Pageable pageable) {
        Page<Restaurant> page = StringUtils.hasText(search)
                ? restaurantRepository.findByNameContainingIgnoreCase(search.trim(), pageable)
                : restaurantRepository.findAll(pageable);
        return page.map(this::toSummary);
    }

    private TenantSummaryDto toSummary(Restaurant r) {
        // Reuse the gate's computed expiry/grace/read-only state instead of recomputing it here.
        BillingStatusDto b = planGateService.getBillingStatus(r.getId());
        return TenantSummaryDto.builder()
                .restaurantId(r.getId())
                .name(r.getName())
                .active(Boolean.TRUE.equals(r.getActive()))
                .planCode(b.getPlanCode())
                .planName(b.getPlanName())
                .isTrial(b.getIsTrial())
                .planExpiresAt(b.getPlanExpiresAt())
                .daysUntilExpiry(b.getDaysUntilExpiry())
                .inGracePeriod(b.getInGracePeriod())
                .readOnly(b.getReadOnly())
                .build();
    }

    /** Point any tenant at a plan. Delegates to the audited, cache-invalidating per-tenant path. */
    public BillingStatusDto changePlan(Long restaurantId, ChangePlanRequest request, UserPrincipal actor) {
        SetPlanRequest setPlan = new SetPlanRequest();
        setPlan.setRestaurantId(restaurantId);
        setPlan.setPlanCode(request.getPlanCode());
        setPlan.setPlanExpiresAt(request.getPlanExpiresAt());
        setPlan.setIsTrial(request.getIsTrial());
        return planGateService.setPlan(setPlan, actor);
    }

    /**
     * Push a tenant's expiry out by {@code days}. Extends from the current expiry when it is still in
     * the future (so granted days aren't lost), otherwise from now (reviving a lapsed plan).
     */
    @Transactional
    public BillingStatusDto extendPlan(Long restaurantId, int days, UserPrincipal actor) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found: " + restaurantId));
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime previous = restaurant.getPlanExpiresAt();
        LocalDateTime base = previous != null && previous.isAfter(now) ? previous : now;
        LocalDateTime updated = base.plusDays(days);

        restaurant.setPlanExpiresAt(updated);
        restaurantRepository.save(restaurant);
        planGateService.invalidate(restaurantId);

        auditService.logAction(AuditService.AuditLogBuilder.create()
                .action(AuditAction.PLAN_CHANGED)
                .entityType("Restaurant")
                .entityId(restaurantId)
                .restaurantId(restaurantId)
                .userId(actor != null ? actor.getId() : null)
                .username(actor != null ? actor.getUsername() : "system")
                .userRole(actor != null && actor.getRole() != null ? actor.getRole().name() : null)
                .previousValue(String.valueOf(previous))
                .newValue(String.valueOf(updated))
                .actionDetail("Plan expiry extended by " + days + " day(s) via platform console"));

        log.info("Plan expiry extended for restaurant {} by {} days: {} -> {}", restaurantId, days, previous, updated);
        return planGateService.getBillingStatus(restaurantId);
    }

    /** Suspend ({@code active = false}) or reactivate ({@code active = true}) a tenant. Audited. */
    @Transactional
    public TenantSummaryDto setActive(Long restaurantId, boolean active, UserPrincipal actor) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new IllegalArgumentException("Restaurant not found: " + restaurantId));
        boolean previous = Boolean.TRUE.equals(restaurant.getActive());

        restaurant.setActive(active);
        restaurantRepository.save(restaurant);

        auditService.logAction(AuditService.AuditLogBuilder.create()
                .action(active ? AuditAction.RESTAURANT_REACTIVATED : AuditAction.RESTAURANT_SUSPENDED)
                .entityType("Restaurant")
                .entityId(restaurantId)
                .restaurantId(restaurantId)
                .userId(actor != null ? actor.getId() : null)
                .username(actor != null ? actor.getUsername() : "system")
                .userRole(actor != null && actor.getRole() != null ? actor.getRole().name() : null)
                .previousValue(String.valueOf(previous))
                .newValue(String.valueOf(active))
                .actionDetail((active ? "Restaurant reactivated" : "Restaurant suspended") + " via platform console"));

        log.info("Restaurant {} active set {} -> {} by platform operator", restaurantId, previous, active);
        return toSummary(restaurant);
    }
}
