package com.elcafe.security;

import com.elcafe.modules.customer.entity.Customer;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Custom UserDetails implementation for Customer entities
 * Used for OTP-based authentication
 */
@Data
@AllArgsConstructor
public class CustomerPrincipal implements UserDetails {

    private Long id;
    private String phone;
    private String email;
    private boolean active;

    /**
     * Create CustomerPrincipal from Customer entity
     */
    public static CustomerPrincipal create(Customer customer) {
        return new CustomerPrincipal(
                customer.getId(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getActive()
        );
    }

    /**
     * Create CustomerPrincipal from phone number and customer ID
     * Used when loading from JWT token
     */
    public static CustomerPrincipal create(String phone, Long customerId) {
        return new CustomerPrincipal(
                customerId,
                phone,
                null,
                true
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_CUSTOMER")
        );
    }

    @Override
    public String getPassword() {
        // Customers don't have passwords (OTP-based auth)
        return null;
    }

    @Override
    public String getUsername() {
        // Use phone as username for customers
        return phone;
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
