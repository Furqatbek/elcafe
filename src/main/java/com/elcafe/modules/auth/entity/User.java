package com.elcafe.modules.auth.entity;

import com.elcafe.modules.auth.enums.UserRole;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "users")
@EntityListeners(AuditingEntityListener.class)
// Phase 0 §3.4: User is DELIBERATELY NOT tenant-@Filtered (do not "complete the sweep" by adding
// it — TenantFilterPolicyTest guards this). Reasons:
//  1. It is the Spring Security principal, loaded during authentication (JwtAuthenticationFilter →
//     loadUserByUsername) before the request-scoped tenant filter is enabled, so filtering it would
//     not even cover the auth path.
//  2. It is the target of many @ManyToOne associations across tenant entities (employee, approvedBy,
//     operator, appliedBy, confirmedBy, createdBy, ...), several EAGER and several nullable=false.
//     With the filter active in a tenant request, a required association pointing at a platform
//     (restaurant_id IS NULL) or cross-tenant user would resolve to nothing → FetchNotFoundException
//     (HTTP 500) in legitimate flows.
// Cross-tenant user *enumeration* (e.g. listing endpoints) is instead constrained at the
// query/controller layer, which can scope by restaurant without breaking association fetches.
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(name = "restaurant_id")
    private Long restaurantId;

    private String resetToken;

    private LocalDateTime resetTokenExpiry;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
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
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
