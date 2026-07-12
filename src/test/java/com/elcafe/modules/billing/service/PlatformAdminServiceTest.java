package com.elcafe.modules.billing.service;

import com.elcafe.exception.ResourceNotFoundException;

import com.elcafe.common.audit.service.AuditService;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.billing.dto.BillingStatusDto;
import com.elcafe.modules.billing.dto.ChangePlanRequest;
import com.elcafe.modules.billing.dto.SetPlanRequest;
import com.elcafe.modules.billing.dto.TenantSummaryDto;
import com.elcafe.modules.billing.enums.SubscriptionStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformAdminServiceTest {

    @Mock private RestaurantRepository restaurantRepository;
    @Mock private PlanGateService planGateService;
    @Mock private AuditService auditService;
    @Mock private SubscriptionAccessService subscriptionAccessService;
    @Mock private BillingService billingService;

    @InjectMocks private PlatformAdminService service;

    private UserPrincipal actor;

    @BeforeEach
    void setUp() {
        actor = new UserPrincipal(1L, "ops@platform.test", "x", UserRole.SUPER_ADMIN, true, null);
    }

    private Restaurant restaurant(Long id, String name, boolean active) {
        Restaurant r = new Restaurant();
        r.setId(id);
        r.setName(name);
        r.setActive(active);
        return r;
    }

    @Test
    @DisplayName("listTenants merges restaurant identity with the gate's computed billing state")
    void listTenants_mapsFields() {
        when(restaurantRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(restaurant(7L, "Cafe X", true))));
        when(planGateService.getBillingStatus(7L)).thenReturn(BillingStatusDto.builder()
                .restaurantId(7L).planCode("pro").planName("Pro").isTrial(false)
                .daysUntilExpiry(10L).inGracePeriod(false).readOnly(false).build());

        Page<TenantSummaryDto> page = service.listTenants(null, Pageable.ofSize(20));

        assertThat(page).hasSize(1);
        TenantSummaryDto dto = page.getContent().get(0);
        assertThat(dto.getRestaurantId()).isEqualTo(7L);
        assertThat(dto.getName()).isEqualTo("Cafe X");
        assertThat(dto.isActive()).isTrue();
        assertThat(dto.getPlanCode()).isEqualTo("pro");
        assertThat(dto.getDaysUntilExpiry()).isEqualTo(10L);
        assertThat(dto.getReadOnly()).isFalse();
    }

    @Test
    @DisplayName("listTenants with a search term uses the trimmed name query, not findAll")
    void listTenants_withSearch_usesNameQuery() {
        when(restaurantRepository.findByNameContainingIgnoreCase(eq("cafe"), any()))
                .thenReturn(new PageImpl<>(List.of()));

        service.listTenants("  cafe  ", Pageable.ofSize(20));

        verify(restaurantRepository).findByNameContainingIgnoreCase(eq("cafe"), any(Pageable.class));
        verify(restaurantRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    @DisplayName("changePlan forwards the path id + body to the audited per-tenant setPlan")
    void changePlan_delegatesToSetPlan() {
        ChangePlanRequest req = new ChangePlanRequest();
        req.setPlanCode("advance");
        req.setIsTrial(true);
        LocalDateTime exp = LocalDateTime.now().plusDays(30);
        req.setPlanExpiresAt(exp);
        when(planGateService.setPlan(any(), any())).thenReturn(BillingStatusDto.builder().planCode("advance").build());

        service.changePlan(42L, req, actor);

        ArgumentCaptor<SetPlanRequest> captor = ArgumentCaptor.forClass(SetPlanRequest.class);
        verify(planGateService).setPlan(captor.capture(), eq(actor));
        SetPlanRequest sent = captor.getValue();
        assertThat(sent.getRestaurantId()).isEqualTo(42L);
        assertThat(sent.getPlanCode()).isEqualTo("advance");
        assertThat(sent.getPlanExpiresAt()).isEqualTo(exp);
        assertThat(sent.getIsTrial()).isTrue();
    }

    @Test
    @DisplayName("extendPlan with a future expiry adds the days to the existing expiry (no days lost)")
    void extendPlan_fromFutureExpiry() {
        LocalDateTime future = LocalDateTime.now().plusDays(5);
        Restaurant r = restaurant(1L, "X", true);
        r.setPlanExpiresAt(future);
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(r));
        when(planGateService.getBillingStatus(1L)).thenReturn(BillingStatusDto.builder().build());

        service.extendPlan(1L, 10, actor);

        assertThat(r.getPlanExpiresAt()).isEqualTo(future.plusDays(10));
        verify(restaurantRepository).save(r);
        verify(planGateService).invalidate(1L);
        verify(auditService).logAction(any());
    }

    @Test
    @DisplayName("extendPlan on a lapsed plan extends from now, reviving it")
    void extendPlan_fromLapsedExpiry() {
        Restaurant r = restaurant(1L, "X", true);
        r.setPlanExpiresAt(LocalDateTime.now().minusDays(20));
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(r));
        when(planGateService.getBillingStatus(1L)).thenReturn(BillingStatusDto.builder().build());

        LocalDateTime lowerBound = LocalDateTime.now().plusDays(7).minusMinutes(1);
        service.extendPlan(1L, 7, actor);

        // From now (~+7d), not from the 20-days-ago expiry (which would still be in the past).
        assertThat(r.getPlanExpiresAt()).isAfter(lowerBound);
        assertThat(r.getPlanExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("extendPlan on an unknown restaurant throws")
    void extendPlan_unknownRestaurant() {
        when(restaurantRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.extendPlan(99L, 10, actor))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("suspend flips active to false and returns the updated summary")
    void suspend_setsInactive() {
        Restaurant r = restaurant(3L, "X", true);
        when(restaurantRepository.findById(3L)).thenReturn(Optional.of(r));
        when(planGateService.getBillingStatus(3L)).thenReturn(BillingStatusDto.builder().build());

        TenantSummaryDto dto = service.setActive(3L, false, actor);

        assertThat(r.getActive()).isFalse();
        assertThat(dto.isActive()).isFalse();
        verify(restaurantRepository).save(r);
        verify(auditService).logAction(any());
        verify(subscriptionAccessService).invalidate(3L); // gate refreshed immediately
    }

    @Test
    @DisplayName("reactivate flips active back to true")
    void reactivate_setsActive() {
        Restaurant r = restaurant(3L, "X", false);
        when(restaurantRepository.findById(3L)).thenReturn(Optional.of(r));
        when(planGateService.getBillingStatus(3L)).thenReturn(BillingStatusDto.builder().build());

        TenantSummaryDto dto = service.setActive(3L, true, actor);

        assertThat(r.getActive()).isTrue();
        assertThat(dto.isActive()).isTrue();
    }

    @Test
    @DisplayName("cancel terminates the subscription: CANCELLED + inactive, audited, gate invalidated")
    void cancel_terminates() {
        Restaurant r = restaurant(9L, "X", true);
        when(restaurantRepository.findById(9L)).thenReturn(Optional.of(r));
        when(planGateService.getBillingStatus(9L)).thenReturn(BillingStatusDto.builder().build());

        TenantSummaryDto dto = service.cancel(9L, actor);

        assertThat(r.getActive()).isFalse();
        assertThat(r.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        assertThat(dto.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        verify(restaurantRepository).save(r);
        verify(subscriptionAccessService).invalidate(9L);
        verify(auditService).logAction(any());
    }
}
