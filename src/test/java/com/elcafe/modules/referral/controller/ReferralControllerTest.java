package com.elcafe.modules.referral.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.referral.service.ReferralService;
import com.elcafe.security.CustomerPrincipal;
import com.elcafe.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The consumer-facing referral endpoints (audit #25): a CUSTOMER token may only act on its OWN
 * customerId, while staff (a UserPrincipal, not a CustomerPrincipal) may pass any. Verified by driving
 * the controller methods directly with the relevant principal in the SecurityContext.
 */
class ReferralControllerTest {

    private final ReferralService referralService = mock(ReferralService.class);
    private final RestaurantAuthorizationService authz = mock(RestaurantAuthorizationService.class);
    private final ReferralController controller = new ReferralController(referralService, authz);

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void asConsumer(Long customerId) {
        CustomerPrincipal p = CustomerPrincipal.create("+998900000000", customerId);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    private void asAdmin() {
        UserPrincipal p = new UserPrincipal(1L, "admin@test.com", "pw", UserRole.ADMIN, true, 1L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    @Test
    @DisplayName("consumer reading its OWN referrals is allowed")
    void consumer_ownId_ok() {
        asConsumer(100L);
        when(referralService.getCustomerReferrals(1L, 100L)).thenReturn(List.of());
        assertThatCode(() -> controller.getCustomerReferrals(1L, 100L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("consumer reading ANOTHER customer's referrals is denied (horizontal IDOR closed)")
    void consumer_otherId_throws() {
        asConsumer(100L);
        assertThatThrownBy(() -> controller.getCustomerReferrals(1L, 101L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("consumer generating a code for another customer is denied")
    void consumer_generateForOther_throws() {
        asConsumer(100L);
        assertThatThrownBy(() -> controller.generateCode(1L, 101L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("staff (ADMIN) may read any customer's referrals")
    void admin_anyId_ok() {
        asAdmin();
        when(referralService.getCustomerReferrals(1L, 101L)).thenReturn(List.of());
        assertThatCode(() -> controller.getCustomerReferrals(1L, 101L)).doesNotThrowAnyException();
    }
}
