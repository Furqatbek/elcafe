package com.elcafe.modules.auth.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the Phase 0 §3.2/§3.3 residual: a platform/legacy account ({@code restaurant_id IS NULL})
 * must only be mutable by the cross-tenant operator. {@code checkAccess(null)} alone treats a null
 * restaurantId as an unconstrained aggregate load, so without the {@code requireAccess} guard a
 * tenant admin could take over or deactivate such an account via a guessed id.
 */
@ExtendWith(MockitoExtension.class)
class SystemUserControllerTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RestaurantAuthorizationService authz;
    @InjectMocks SystemUserController controller;

    @Test
    @DisplayName("update — non-operator cannot modify a null-restaurant (platform) account via a guessed id")
    void update_deniesNullRestaurantTargetForTenantAdmin() {
        User platform = User.builder().id(42L).restaurantId(null).build();
        when(userRepository.findById(42L)).thenReturn(Optional.of(platform));
        when(authz.isAdmin()).thenReturn(false); // tenant admin, not SUPER_ADMIN

        var req = new SystemUserController.UpdateRequest(null, null, null, "newpass", null, null);

        assertThrows(AccessDeniedException.class, () -> controller.update(42L, req));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("deactivate — non-operator cannot deactivate a null-restaurant (platform) account")
    void deactivate_deniesNullRestaurantTargetForTenantAdmin() {
        User platform = User.builder().id(42L).restaurantId(null).build();
        when(userRepository.findById(42L)).thenReturn(Optional.of(platform));
        when(authz.isAdmin()).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> controller.deactivate(42L));
        verify(userRepository, never()).save(any());
    }
}
