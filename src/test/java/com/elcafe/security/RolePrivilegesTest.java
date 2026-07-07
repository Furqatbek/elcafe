package com.elcafe.security;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behaviour of the SUPER_ADMIN superset and the UNKNOWN sentinel across the
 * two UserDetails implementations (UserPrincipal and the User entity).
 */
class RolePrivilegesTest {

    private static Set<String> authorityNames(Iterable<? extends GrantedAuthority> auths) {
        return java.util.stream.StreamSupport.stream(auths.spliterator(), false)
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    // ---- UserRole helpers ----

    @Test
    @DisplayName("isAdminLevel true for ADMIN and SUPER_ADMIN only")
    void isAdminLevel() {
        assertThat(UserRole.ADMIN.isAdminLevel()).isTrue();
        assertThat(UserRole.SUPER_ADMIN.isAdminLevel()).isTrue();
        assertThat(UserRole.OWNER.isAdminLevel()).isFalse();
        assertThat(UserRole.CASHIER.isAdminLevel()).isFalse();
        assertThat(UserRole.UNKNOWN.isAdminLevel()).isFalse();
    }

    @Test
    @DisplayName("SUPER_ADMIN grants both ROLE_SUPER_ADMIN and ROLE_ADMIN")
    void superAdminGrantsAdminSuperset() {
        assertThat(UserRole.SUPER_ADMIN.grantedRoleNames())
                .containsExactlyInAnyOrder("SUPER_ADMIN", "ADMIN");
    }

    @Test
    @DisplayName("UNKNOWN grants nothing and is not usable")
    void unknownGrantsNothing() {
        assertThat(UserRole.UNKNOWN.grantedRoleNames()).isEmpty();
        assertThat(UserRole.UNKNOWN.isUsable()).isFalse();
        assertThat(UserRole.ADMIN.isUsable()).isTrue();
    }

    // ---- UserPrincipal ----

    @Test
    @DisplayName("UserPrincipal SUPER_ADMIN carries ROLE_ADMIN so hasRole('ADMIN') passes")
    void principalSuperAdminAuthorities() {
        UserPrincipal p = new UserPrincipal(1L, "su@test.com", "pw", UserRole.SUPER_ADMIN, true, null);
        assertThat(authorityNames(p.getAuthorities()))
                .containsExactlyInAnyOrder("ROLE_SUPER_ADMIN", "ROLE_ADMIN");
        assertThat(p.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("UserPrincipal ordinary role is unchanged (single authority)")
    void principalOrdinaryRole() {
        UserPrincipal p = new UserPrincipal(1L, "op@test.com", "pw", UserRole.OPERATOR, true, 1L);
        assertThat(authorityNames(p.getAuthorities())).containsExactly("ROLE_OPERATOR");
    }

    @Test
    @DisplayName("UserPrincipal UNKNOWN is disabled and holds no authorities")
    void principalUnknownDisabled() {
        UserPrincipal p = new UserPrincipal(1L, "bad@test.com", "pw", UserRole.UNKNOWN, true, 1L);
        assertThat(p.getAuthorities()).isEmpty();
        assertThat(p.isEnabled()).isFalse();
    }

    // ---- User entity ----

    @Test
    @DisplayName("User entity SUPER_ADMIN also carries the ADMIN superset")
    void userEntitySuperAdminAuthorities() {
        User u = User.builder()
                .email("su@test.com").password("pw").firstName("Su").lastName("Admin")
                .role(UserRole.SUPER_ADMIN).active(true).build();
        assertThat(authorityNames(u.getAuthorities()))
                .containsExactlyInAnyOrder("ROLE_SUPER_ADMIN", "ROLE_ADMIN");
        assertThat(u.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("User entity UNKNOWN role is disabled with no authorities")
    void userEntityUnknownDisabled() {
        User u = User.builder()
                .email("bad@test.com").password("pw").firstName("B").lastName("D")
                .role(UserRole.UNKNOWN).active(true).build();
        assertThat(u.getAuthorities()).isEmpty();
        assertThat(u.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("active=false still disables even a valid role")
    void inactiveDisabled() {
        UserPrincipal p = new UserPrincipal(1L, "x@test.com", "pw", UserRole.ADMIN, false, 1L);
        assertThat(p.isEnabled()).isFalse();
    }
}
