package com.elcafe.modules.auth.service;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private CustomerRepository customerRepository;
    @InjectMocks private CustomUserDetailsService customUserDetailsService;

    private User adminUser;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(1L).email("admin@test.com").password("encoded")
                .firstName("Admin").lastName("User")
                .role(UserRole.ADMIN).active(true).restaurantId(1L).build();
    }

    @Test @DisplayName("loadUserByUsername — user found by email")
    void loadUserByUsername_userFound() {
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(adminUser));

        UserDetails result = customUserDetailsService.loadUserByUsername("admin@test.com");

        assertThat(result).isInstanceOf(UserPrincipal.class);
        assertThat(result.getUsername()).isEqualTo("admin@test.com");
        assertThat(result.getAuthorities()).extracting("authority").contains("ROLE_ADMIN");
    }

    @Test @DisplayName("loadUserByUsername — user found by phone")
    void loadUserByUsername_userFoundByPhone() {
        when(userRepository.findByEmail("+998901234567")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("+998901234567")).thenReturn(Optional.of(adminUser));

        UserDetails result = customUserDetailsService.loadUserByUsername("+998901234567");

        assertThat(result).isInstanceOf(UserPrincipal.class);
    }

    @Test @DisplayName("loadUserByUsername — customer account gives helpful message")
    void loadUserByUsername_customerFound() {
        when(userRepository.findByEmail("customer@test.com")).thenReturn(Optional.empty());
        when(customerRepository.findByEmail("customer@test.com")).thenReturn(Optional.of(new Customer()));

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("customer@test.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("customer account")
                .hasMessageContaining("OTP");
    }

    @Test @DisplayName("loadUserByUsername — not found throws")
    void loadUserByUsername_notFound_throws() {
        when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());
        when(customerRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("unknown@test.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User not found");
    }
}
