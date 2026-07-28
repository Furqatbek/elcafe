package com.elcafe.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the role hierarchy, because both edges in it stand in for role lists nobody edits by hand.
 *
 * <p><b>SUPER_ADMIN &gt; ADMIN</b> lets the platform operator satisfy every {@code hasRole('ADMIN')}
 * gate. <b>OWNER &gt; MANAGER</b> lets the restaurant owner satisfy the ~87 endpoints gated
 * {@code hasAnyRole('ADMIN','MANAGER')} that never listed OWNER — the gap that 403'd an owner adding a
 * table from the floor map. Both are invisible at the call site (a reader of
 * {@code @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")} would not guess an OWNER passes), so a test is
 * the only thing that keeps them from being "cleaned up" by someone who does not know why they exist.
 */
class RoleHierarchyTest {

    private final RoleHierarchy hierarchy = SecurityConfig.roleHierarchy();

    private List<String> reachableFrom(String role) {
        List<GrantedAuthority> granted = List.of(new SimpleGrantedAuthority(role));
        return hierarchy.getReachableGrantedAuthorities(granted).stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }

    @Test
    @DisplayName("OWNER inherits MANAGER — the fix for the 87 owner-excluded management endpoints")
    void ownerInheritsManager() {
        assertThat(reachableFrom("ROLE_OWNER")).contains("ROLE_OWNER", "ROLE_MANAGER");
    }

    @Test
    @DisplayName("SUPER_ADMIN still inherits ADMIN")
    void superAdminInheritsAdmin() {
        assertThat(reachableFrom("ROLE_SUPER_ADMIN")).contains("ROLE_SUPER_ADMIN", "ROLE_ADMIN");
    }

    /**
     * OWNER and ADMIN are peers. If OWNER silently gained ADMIN, it would reach ADMIN-only endpoints
     * (e.g. deleting a table) that are deliberately narrower — so this stays denied on purpose.
     */
    @Test
    @DisplayName("OWNER does NOT become ADMIN — the two remain peers")
    void ownerDoesNotInheritAdmin() {
        assertThat(reachableFrom("ROLE_OWNER")).doesNotContain("ROLE_ADMIN", "ROLE_SUPER_ADMIN");
    }

    /**
     * The owner edge stops at MANAGER. A purely front-of-house WAITER-only capability is not something
     * the hierarchy grants an owner; where an owner needs it, the endpoint lists MANAGER too (which the
     * owner now inherits). Pinned so nobody extends the chain to WAITER without meaning to.
     */
    @Test
    @DisplayName("MANAGER does not inherit WAITER, so the OWNER edge does not silently reach WAITER-only")
    void ownerEdgeStopsAtManager() {
        assertThat(reachableFrom("ROLE_MANAGER")).doesNotContain("ROLE_WAITER");
        assertThat(reachableFrom("ROLE_OWNER")).doesNotContain("ROLE_WAITER");
    }
}
