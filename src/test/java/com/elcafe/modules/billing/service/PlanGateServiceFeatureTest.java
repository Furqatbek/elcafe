package com.elcafe.modules.billing.service;

import com.elcafe.common.audit.service.AuditService;
import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.ForbiddenException;
import com.elcafe.modules.billing.entity.SubscriptionPlan;
import com.elcafe.modules.billing.repository.SubscriptionPlanRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanGateServiceFeatureTest {

    @Mock private SubscriptionPlanRepository planRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @Mock private RestaurantAuthorizationService authorizationService;
    @Mock private AuditService auditService;

    private PlanGateService service;

    @BeforeEach
    void setUp() {
        service = new PlanGateService(planRepository, restaurantRepository, authorizationService, auditService);
    }

    /** Wires restaurant {@code id} to a plan with the given codes; {@code null} codes = no plan. */
    private void restaurantWith(long id, Set<String> codes) {
        Restaurant r = mock(Restaurant.class);
        if (codes == null) {
            when(r.getPlan()).thenReturn(null);
        } else {
            SubscriptionPlan plan = mock(SubscriptionPlan.class);
            when(plan.getCode()).thenReturn("x");
            when(plan.getName()).thenReturn("X");
            when(plan.getFeatureCodes()).thenReturn(codes);
            when(r.getPlan()).thenReturn(plan);
        }
        when(restaurantRepository.findByIdWithPlanFeatures(id)).thenReturn(Optional.of(r));
    }

    @Test
    @DisplayName("plan has the feature → allowed")
    void planHasFeature_allows() {
        restaurantWith(1L, Set.of("inventory"));
        assertThatCode(() -> service.requireFeatureIfPlanned(1L, "inventory")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("plan lacks the feature → 403 plan.feature_required")
    void planLacksFeature_throws() {
        restaurantWith(2L, Set.of());
        assertThatThrownBy(() -> service.requireFeatureIfPlanned(2L, "inventory"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("plan.feature_required:inventory");
    }

    @Test
    @DisplayName("restaurant has no plan → allowed (prod is NOT NULL; this protects tests/edge cases)")
    void noPlan_allows() {
        restaurantWith(3L, null);
        assertThatCode(() -> service.requireFeatureIfPlanned(3L, "inventory")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("restaurant not found → allowed (no plan to gate on)")
    void restaurantMissing_allows() {
        when(restaurantRepository.findByIdWithPlanFeatures(4L)).thenReturn(Optional.empty());
        assertThatCode(() -> service.requireFeatureIfPlanned(4L, "inventory")).doesNotThrowAnyException();
    }
}
