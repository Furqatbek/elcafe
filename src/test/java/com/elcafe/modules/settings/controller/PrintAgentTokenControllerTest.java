package com.elcafe.modules.settings.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrintAgentTokenControllerTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private RestaurantAuthorizationService authz;
    @InjectMocks private PrintAgentTokenController controller;

    @Test
    void mintsTokenScopedToTheCallersOwnRestaurant() {
        when(authz.getCurrentUserRestaurantId()).thenReturn(5L);
        when(jwtUtil.generatePrintAgentToken(5L)).thenReturn("the-token");

        var body = controller.mintToken().getBody();

        assertThat(body).isNotNull();
        assertThat(body.getData()).containsEntry("token", "the-token").containsEntry("restaurantId", 5L);
        verify(jwtUtil).generatePrintAgentToken(5L); // never a client-supplied restaurant
    }

    @Test
    void deniesACallerWithNoRestaurant() {
        when(authz.getCurrentUserRestaurantId()).thenReturn(null);
        assertThatThrownBy(() -> controller.mintToken()).isInstanceOf(AccessDeniedException.class);
    }
}
