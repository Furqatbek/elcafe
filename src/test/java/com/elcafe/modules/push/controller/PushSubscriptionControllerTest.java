package com.elcafe.modules.push.controller;

import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.service.CustomerService;
import com.elcafe.modules.push.dto.PushSubscriptionRequest;
import com.elcafe.modules.push.entity.PushSubscription;
import com.elcafe.modules.push.service.WebPushService;
import com.elcafe.security.CustomerPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the MIG-13 fix: the consumer push subscription resolves the customer from the token's
 * authoritative customer id ({@link CustomerPrincipal}), NOT from an unscoped phone lookup — the
 * same phone can exist as customer rows in several restaurants (V150 fragmentation), and the old
 * single-result {@code findByPhone} threw for exactly those consumers (HTTP 500).
 */
@ExtendWith(MockitoExtension.class)
class PushSubscriptionControllerTest {

    @Mock private WebPushService webPushService;
    @Mock private com.elcafe.modules.push.repository.PushSubscriptionRepository subscriptionRepository;
    @Mock private CustomerService customerService;
    @Mock private com.elcafe.modules.auth.repository.UserRepository userRepository;
    @InjectMocks private PushSubscriptionController controller;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("subscribeCustomer resolves the customer by the token's id, never by phone")
    void subscribeCustomerUsesTokenCustomerId() {
        CustomerPrincipal principal = CustomerPrincipal.create("+998900000001", 42L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        Customer customer = new Customer();
        customer.setId(42L);
        when(customerService.getCustomerById(42L)).thenReturn(customer);

        PushSubscription subscription = new PushSubscription();
        subscription.setId(7L);
        when(webPushService.subscribeCustomer(eq(customer), any())).thenReturn(subscription);

        PushSubscriptionRequest request = new PushSubscriptionRequest();
        request.setEndpoint("https://push.example/ep");
        request.setP256dh("key");
        request.setAuth("auth");

        var response = controller.subscribeCustomer(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(customerService).getCustomerById(42L);
        verify(customerService, never()).findByPhone(any());
    }
}
