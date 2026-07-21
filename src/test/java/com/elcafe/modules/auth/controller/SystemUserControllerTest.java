package com.elcafe.modules.auth.controller;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.repository.UserRepository;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SystemUserControllerTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private SystemUserController controller;

    private User superAdminTarget;

    @BeforeEach
    void setUp() {
        superAdminTarget = User.builder()
                .id(50L).email("su@test.com").password("$enc")
                .firstName("Su").lastName("Admin")
                .role(UserRole.SUPER_ADMIN).active(true).build();
        when(userRepository.findById(50L)).thenReturn(Optional.of(superAdminTarget));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }

    private UserPrincipal caller(UserRole role) {
        return new UserPrincipal(1L, "caller@test.com", "pw", role, true, 1L);
    }

    @Test
    @DisplayName("plain ADMIN cannot update a SUPER_ADMIN account")
    void adminCannotUpdateSuperAdmin() {
        var req = new SystemUserController.UpdateRequest(null, null, null, "hijacked", null, null, null);

        assertThatThrownBy(() -> controller.update(50L, req, caller(UserRole.ADMIN)))
                .isInstanceOf(AccessDeniedException.class);
        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("plain ADMIN cannot deactivate a SUPER_ADMIN account")
    void adminCannotDeactivateSuperAdmin() {
        assertThatThrownBy(() -> controller.deactivate(50L, caller(UserRole.ADMIN)))
                .isInstanceOf(AccessDeniedException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("a SUPER_ADMIN may update another SUPER_ADMIN")
    void superAdminCanUpdateSuperAdmin() {
        when(passwordEncoder.encode("newpw")).thenReturn("$enc2");
        var req = new SystemUserController.UpdateRequest(null, null, null, "newpw", null, null, null);

        controller.update(50L, req, caller(UserRole.SUPER_ADMIN));

        verify(userRepository).save(superAdminTarget);
    }

    @Test
    @DisplayName("guard does not interfere with ordinary (non-super-admin) targets")
    void adminCanUpdateOrdinaryUser() {
        User ordinary = User.builder()
                .id(60L).email("op@test.com").password("$enc")
                .firstName("Op").lastName("User")
                .role(UserRole.OPERATOR).active(true).build();
        when(userRepository.findById(60L)).thenReturn(Optional.of(ordinary));
        var req = new SystemUserController.UpdateRequest("Renamed", null, null, null, null, null, null);

        controller.update(60L, req, caller(UserRole.ADMIN));

        assertThat(ordinary.getFirstName()).isEqualTo("Renamed");
        verify(userRepository).save(ordinary);
    }

    @Test
    @DisplayName("creating an OWNER binds it to the given restaurant")
    void createOwnerWithRestaurant() {
        when(userRepository.existsByEmail("owner@test.com")).thenReturn(false);
        when(passwordEncoder.encode("pw")).thenReturn("$enc");
        var req = new SystemUserController.CreateRequest(
                "owner@test.com", "pw", "Furqat", "Owner", "+998900000000",
                UserRole.OWNER, 7L);

        var response = controller.create(req);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(UserRole.OWNER);
        assertThat(saved.getValue().getRestaurantId()).isEqualTo(7L);
        assertThat(saved.getValue().getActive()).isTrue();
        assertThat(saved.getValue().getEmailVerified()).isTrue();
        // the response body echoes the restaurant back to the panel
        assertThat(response.getBody().getData()).containsEntry("restaurantId", 7L);
    }

    @Test
    @DisplayName("updating restaurantId reassigns, and null unlinks the branch")
    void updateReassignsAndClearsRestaurant() {
        User owner = User.builder()
                .id(70L).email("o@test.com").password("$enc")
                .firstName("O").lastName("Wner")
                .role(UserRole.OWNER).active(true).restaurantId(7L).build();
        when(userRepository.findById(70L)).thenReturn(Optional.of(owner));

        // reassign to another branch
        controller.update(70L,
                new SystemUserController.UpdateRequest(null, null, null, null, null, null, 9L),
                caller(UserRole.ADMIN));
        assertThat(owner.getRestaurantId()).isEqualTo(9L);

        // null unlinks the branch entirely
        controller.update(70L,
                new SystemUserController.UpdateRequest(null, null, null, null, null, null, null),
                caller(UserRole.ADMIN));
        assertThat(owner.getRestaurantId()).isNull();
    }
}
