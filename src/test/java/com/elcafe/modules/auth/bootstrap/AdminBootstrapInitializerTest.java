package com.elcafe.modules.auth.bootstrap;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the first-boot operator bootstrap that replaced the removed V2 seed admin: a SUPER_ADMIN is
 * created from ADMIN_EMAIL/ADMIN_PASSWORD exactly once — only on an empty users table — and never
 * when the variables are absent (that case must merely warn, not crash the boot).
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapInitializerTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private AdminBootstrapInitializer initializer(String email, String password) {
        return new AdminBootstrapInitializer(userRepository, passwordEncoder, email, password);
    }

    @Test
    @DisplayName("empty users table + both vars set → creates an active, verified SUPER_ADMIN")
    void createsSuperAdminOnEmptyDatabase() {
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("S3cure-Pass!")).thenReturn("{bcrypt}encoded");

        initializer(" Admin@Example.COM ", "S3cure-Pass!").run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("admin@example.com");
        assertThat(saved.getPassword()).isEqualTo("{bcrypt}encoded");
        assertThat(saved.getRole()).isEqualTo(UserRole.SUPER_ADMIN);
        assertThat(saved.getActive()).isTrue();
        assertThat(saved.getEmailVerified()).isTrue();
        assertThat(saved.getRestaurantId()).isNull();
        assertThat(saved.getTokenVersion()).isZero();
    }

    @Test
    @DisplayName("users already exist → never creates anything, even with vars set")
    void skipsWhenUsersExist() {
        when(userRepository.count()).thenReturn(5L);

        initializer("admin@example.com", "S3cure-Pass!").run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("empty table but vars absent → warns and skips instead of failing the boot")
    void skipsWhenVariablesMissing() {
        when(userRepository.count()).thenReturn(0L);

        initializer("", "").run(null);
        initializer("admin@example.com", " ").run(null);
        initializer(null, null).run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("users exist + ADMIN_EMAIL set for a missing user → checks existence, creates nothing")
    void warnsWhenAdminEmailSetButDatabaseNotEmpty() {
        when(userRepository.count()).thenReturn(3L);
        when(userRepository.existsByEmail("newadmin@example.com")).thenReturn(false);

        initializer("NewAdmin@Example.com", "S3cure-Pass!").run(null);

        // The diagnostic path normalises the email and consults the DB, then creates nothing.
        verify(userRepository).existsByEmail("newadmin@example.com");
        verify(userRepository, never()).save(any());
    }
}
