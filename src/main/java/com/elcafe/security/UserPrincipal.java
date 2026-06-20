package com.elcafe.security;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Custom UserDetails implementation that wraps the User entity
 */
@Data
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private Long id;
    private String email;
    private String password;
    private UserRole role;
    private boolean active;
    private Long restaurantId;
    private int tokenVersion;

    /**
     * Backwards-compatible constructor for callers that don't track the token version
     * (defaults {@code tokenVersion} to 0).
     */
    public UserPrincipal(Long id, String email, String password, UserRole role,
                         boolean active, Long restaurantId) {
        this(id, email, password, role, active, restaurantId, 0);
    }

    /**
     * Create UserPrincipal from User entity
     */
    public static UserPrincipal create(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.getRole(),
                user.getActive(),
                user.getRestaurantId(),
                user.getTokenVersion() == null ? 0 : user.getTokenVersion()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + role.name())
        );
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
