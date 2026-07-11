package com.elcafe.modules.auth.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.common.tenant.TenantContext;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    @Mock RestaurantRepository restaurantRepository;
    @InjectMocks SystemUserController controller;

    @Test
    @DisplayName("update — non-operator cannot modify a null-restaurant (platform) account via a guessed id")
    void update_deniesNullRestaurantTargetForTenantAdmin() {
        User platform = User.builder().id(42L).restaurantId(null).build();
        when(userRepository.findById(42L)).thenReturn(Optional.of(platform));
        when(authz.isAdmin()).thenReturn(false); // tenant admin, not SUPER_ADMIN

        var req = new SystemUserController.UpdateRequest(null, null, null, "newpass", null, null, null);

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

    @Test
    @DisplayName("getAll — a caller with no tenant (deny sentinel) sees no users")
    void getAll_denySentinel_returnsEmpty() {
        when(authz.currentTenantReadScopeStrict()).thenReturn(TenantContext.NO_ACCESS);
        when(userRepository.findByRestaurantId(TenantContext.NO_ACCESS)).thenReturn(List.of());

        var body = controller.getAll().getBody();

        assertNotNull(body);
        assertTrue(body.getData().isEmpty());
    }
    @Test
    @DisplayName("deactivate — bumps tokenVersion, revoking the target's live access/refresh tokens")
    void deactivate_bumpsTokenVersion() {
        User u = User.builder().id(5L).restaurantId(9L).active(true).tokenVersion(3).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(u));

        controller.deactivate(5L);

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertFalse(cap.getValue().getActive());
        assertEquals(4, cap.getValue().getTokenVersion());
    }

    @Test
    @DisplayName("update — an admin password reset bumps tokenVersion (kills old sessions)")
    void update_passwordReset_bumpsTokenVersion() {
        User u = User.builder().id(5L).email("op@t.co").firstName("O").lastName("P").restaurantId(9L).active(true).tokenVersion(0).role(UserRole.OPERATOR).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(u));
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new SystemUserController.UpdateRequest(null, null, null, "newpass", null, null, null);
        controller.update(5L, req);

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertEquals(1, cap.getValue().getTokenVersion());
    }
    @Test
    @DisplayName("create — binds the new user to the creator's restaurant (strict scope, works in shadow)")
    void create_bindsCreatorRestaurant() {
        when(authz.currentTenantScopeStrict()).thenReturn(5L);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(99L); // persistence assigns the id
            return u;
        });

        var req = new SystemUserController.CreateRequest("m@t.co", "pw", "M", "G", null, UserRole.MANAGER, null);
        controller.create(req);

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertEquals(5L, cap.getValue().getRestaurantId());
    }

    // --- explicit restaurant binding (how a restaurant's FIRST admin is attached, docs/LAUNCH.md) ---

    @Test
    @DisplayName("create — SUPER_ADMIN may bind the new user to any existing restaurant")
    void create_superAdminBindsExplicitRestaurant() {
        when(authz.isAdmin()).thenReturn(true);
        when(restaurantRepository.existsById(3L)).thenReturn(true);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(99L);
            return u;
        });

        var req = new SystemUserController.CreateRequest("a@t.co", "pw", "A", "D", null, UserRole.ADMIN, 3L);
        controller.create(req);

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertEquals(3L, cap.getValue().getRestaurantId());
    }

    @Test
    @DisplayName("create — SUPER_ADMIN binding to a nonexistent restaurant is a 400, nothing saved")
    void create_superAdminUnknownRestaurantRejected() {
        when(authz.isAdmin()).thenReturn(true);
        when(restaurantRepository.existsById(77L)).thenReturn(false);
        when(userRepository.existsByEmail(any())).thenReturn(false);

        var req = new SystemUserController.CreateRequest("a@t.co", "pw", "A", "D", null, UserRole.ADMIN, 77L);

        assertThrows(BadRequestException.class, () -> controller.create(req));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("create — a tenant admin supplying another restaurant's id is denied, not rewritten")
    void create_tenantAdminForeignRestaurantDenied() {
        when(authz.isAdmin()).thenReturn(false);
        when(authz.currentTenantScopeStrict()).thenReturn(5L);
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");

        var req = new SystemUserController.CreateRequest("m@t.co", "pw", "M", "G", null, UserRole.MANAGER, 7L);

        assertThrows(AccessDeniedException.class, () -> controller.create(req));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("update — SUPER_ADMIN rebinds a platform (null-restaurant) admin to a restaurant")
    void update_superAdminRebindsUser() {
        User unbound = User.builder().id(42L).email("a@t.co").role(UserRole.ADMIN)
                .restaurantId(null).active(true).tokenVersion(0).build();
        when(userRepository.findById(42L)).thenReturn(Optional.of(unbound));
        when(authz.isAdmin()).thenReturn(true);
        when(restaurantRepository.existsById(3L)).thenReturn(true);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new SystemUserController.UpdateRequest(null, null, null, null, null, null, 3L);
        controller.update(42L, req);

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertEquals(3L, cap.getValue().getRestaurantId());
    }

    @Test
    @DisplayName("update — the edit form echoing the target's current restaurant back is a no-op, not a 403")
    void update_sameRestaurantEchoAllowedForTenantAdmin() {
        User staff = User.builder().id(5L).email("op@t.co").role(UserRole.OPERATOR)
                .restaurantId(9L).active(true).tokenVersion(0).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(staff));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new SystemUserController.UpdateRequest("New", null, null, null, null, null, 9L);
        controller.update(5L, req);

        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(cap.capture());
        assertEquals(9L, cap.getValue().getRestaurantId());
        assertEquals("New", cap.getValue().getFirstName());
    }

    @Test
    @DisplayName("update — a tenant admin cannot move a user to another restaurant")
    void update_tenantAdminRebindDenied() {
        User staff = User.builder().id(5L).email("op@t.co").role(UserRole.OPERATOR)
                .restaurantId(9L).active(true).tokenVersion(0).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(staff));
        when(authz.isAdmin()).thenReturn(false);

        var req = new SystemUserController.UpdateRequest(null, null, null, null, null, null, 3L);

        assertThrows(AccessDeniedException.class, () -> controller.update(5L, req));
        verify(userRepository, never()).save(any());
    }
}
