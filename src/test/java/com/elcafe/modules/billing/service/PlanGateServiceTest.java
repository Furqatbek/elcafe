package com.elcafe.modules.billing.service;

import com.elcafe.common.audit.service.AuditService;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.ForbiddenException;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.billing.dto.BillingStatusDto;
import com.elcafe.modules.billing.dto.SetPlanRequest;
import com.elcafe.modules.billing.entity.SubscriptionPlan;
import com.elcafe.modules.billing.repository.SubscriptionPlanRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanGateServiceTest {

    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private RestaurantAuthorizationService authorizationService;
    @Mock private AuditService auditService;
    @InjectMocks private PlanGateService service;

    private SubscriptionPlan plan(String code, String name, Set<String> features) {
        return SubscriptionPlan.builder()
                .code(code).name(name).monthlyPrice(0L).sortOrder(1).active(true)
                .featureCodes(new LinkedHashSet<>(features))
                .build();
    }

    private Restaurant restaurant(Long id, SubscriptionPlan plan, LocalDateTime expiresAt, boolean trial) {
        return Restaurant.builder()
                .id(id).name("Cafe").address("1 Main St")
                .plan(plan).planExpiresAt(expiresAt).isTrial(trial)
                .build();
    }

    private void stubRestaurant(Restaurant r) {
        when(restaurantRepository.findById(r.getId())).thenReturn(Optional.of(r));
    }

    @Test @DisplayName("getBillingStatus — free plan, no expiry: not grace/read-only, days null")
    void status_noExpiry() {
        stubRestaurant(restaurant(1L, plan("start", "Start", Set.of("pos")), null, false));

        BillingStatusDto s = service.getBillingStatus(1L);

        assertThat(s.getPlanCode()).isEqualTo("start");
        assertThat(s.getFeatureCodes()).containsExactly("pos");
        assertThat(s.getDaysUntilExpiry()).isNull();
        assertThat(s.getInGracePeriod()).isFalse();
        assertThat(s.getReadOnly()).isFalse();
    }

    @Test @DisplayName("getBillingStatus — future expiry: active, positive days remaining")
    void status_futureExpiry() {
        stubRestaurant(restaurant(1L, plan("pro", "Pro", Set.of()), LocalDateTime.now().plusDays(5), true));

        BillingStatusDto s = service.getBillingStatus(1L);

        assertThat(s.getDaysUntilExpiry()).isBetween(4L, 5L);
        assertThat(s.getInGracePeriod()).isFalse();
        assertThat(s.getReadOnly()).isFalse();
        assertThat(s.getIsTrial()).isTrue();
    }

    @Test @DisplayName("getBillingStatus — within 3-day grace: full access, grace flag set")
    void status_gracePeriod() {
        stubRestaurant(restaurant(1L, plan("pro", "Pro", Set.of()), LocalDateTime.now().minusDays(1), false));

        BillingStatusDto s = service.getBillingStatus(1L);

        assertThat(s.getInGracePeriod()).isTrue();
        assertThat(s.getReadOnly()).isFalse();
    }

    @Test @DisplayName("getBillingStatus — past grace: read-only")
    void status_readOnly() {
        stubRestaurant(restaurant(1L, plan("pro", "Pro", Set.of()), LocalDateTime.now().minusDays(4), false));

        BillingStatusDto s = service.getBillingStatus(1L);

        assertThat(s.getReadOnly()).isTrue();
        assertThat(s.getInGracePeriod()).isFalse();
        assertThat(service.isReadOnly(1L)).isTrue();
    }

    @Test @DisplayName("getBillingStatus — null restaurant (e.g. ADMIN) returns empty status")
    void status_nullRestaurant() {
        BillingStatusDto s = service.getBillingStatus(null);
        assertThat(s.getPlanCode()).isNull();
        assertThat(s.getReadOnly()).isNull();
    }

    @Test @DisplayName("hasFeature / requireFeature reflect the plan's feature set")
    void featureGating() {
        stubRestaurant(restaurant(1L, plan("pro", "Pro", Set.of("kitchen.dashboard")), null, false));

        assertThat(service.hasFeature(1L, "kitchen.dashboard")).isTrue();
        assertThat(service.hasFeature(1L, "marketing.sms")).isFalse();

        service.requireFeature(1L, "kitchen.dashboard"); // no throw
        assertThatThrownBy(() -> service.requireFeature(1L, "marketing.sms"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("plan.feature_required:marketing.sms");
    }

    @Test @DisplayName("setPlan — repoints the restaurant, audits PLAN_CHANGED, returns new status")
    void setPlan_changesAndAudits() {
        Restaurant r = restaurant(1L, plan("start", "Start", Set.of()), null, false);
        SubscriptionPlan pro = plan("pro", "Pro", Set.of("kitchen.dashboard"));
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(r));
        when(planRepository.findByCode("pro")).thenReturn(Optional.of(pro));
        when(restaurantRepository.save(any(Restaurant.class))).thenAnswer(i -> i.getArgument(0));

        SetPlanRequest req = new SetPlanRequest();
        req.setRestaurantId(1L);
        req.setPlanCode("pro");
        req.setPlanExpiresAt(LocalDateTime.now().plusDays(30));
        req.setIsTrial(false);
        UserPrincipal admin = new UserPrincipal(7L, "admin@elcafe.uz", "x", UserRole.ADMIN, true, null);

        BillingStatusDto result = service.setPlan(req, admin);

        assertThat(r.getPlan()).isSameAs(pro);
        assertThat(r.getPlanStartedAt()).isNotNull();
        assertThat(r.getPlanExpiresAt()).isNotNull();
        assertThat(result.getPlanCode()).isEqualTo("pro");
        verify(restaurantRepository).save(r);
        verify(auditService).logAction(any());
    }

    @Test @DisplayName("setPlan — unknown plan code rejected")
    void setPlan_unknownCode() {
        Restaurant r = restaurant(1L, plan("start", "Start", Set.of()), null, false);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(r));
        when(planRepository.findByCode("ghost")).thenReturn(Optional.empty());

        SetPlanRequest req = new SetPlanRequest();
        req.setRestaurantId(1L);
        req.setPlanCode("ghost");

        assertThatThrownBy(() -> service.setPlan(req, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown plan code");
        verify(auditService, never()).logAction(any());
    }
}
