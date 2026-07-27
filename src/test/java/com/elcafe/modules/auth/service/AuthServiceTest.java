package com.elcafe.modules.auth.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ConflictException;
import com.elcafe.modules.auth.dto.*;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.mapper.UserMapper;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.security.JwtUtil;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserMapper userMapper;
    @Mock private LoginAttemptService loginAttemptService;
    @Mock private EmailService emailService;
    @Mock private com.elcafe.modules.restaurant.repository.RestaurantRepository restaurantRepository;
    @InjectMocks private AuthService authService;

    private User user;
    private UserResponse userResponse;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L).email("admin@test.com").password("$2a$encoded")
                .firstName("Admin").lastName("User")
                .role(UserRole.ADMIN).active(true).build();

        userResponse = UserResponse.builder()
                .id(1L).email("admin@test.com")
                .firstName("Admin").lastName("User")
                .role(UserRole.ADMIN).active(true).build();

        when(userMapper.toResponse(any(User.class))).thenReturn(userResponse);
    }

    @Test @DisplayName("register — forces OWNER role, bound to the named restaurant (ignores client role)")
    void register_success() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@test.com"); request.setPassword("password123");
        request.setFirstName("New"); request.setLastName("User");
        request.setRestaurantId(7L);

        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(restaurantRepository.existsById(7L)).thenReturn(true);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> { User u = i.getArgument(0); u.setId(2L); return u; });
        when(jwtUtil.generateAccessToken(any())).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("refresh-token");

        AuthResponse result = authService.register(request);

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
        verify(passwordEncoder).encode("password123");

        // SECURITY: registration must always create an unprivileged OWNER — never an
        // ADMIN/SUPER_ADMIN, and never a client-chosen role.
        //
        // The binding assertion used to read `isNull()`, pinning the defect rather than the
        // behaviour: OWNER is tenant-scoped, so an unbound one signs in and sees nothing at all.
        // Every account this endpoint produced was in that state.
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getRole()).isEqualTo(UserRole.OWNER);
        assertThat(savedUser.getValue().getRestaurantId()).isEqualTo(7L);
        assertThat(savedUser.getValue().getEmailVerified()).isFalse();
    }

    /** A dangling id would recreate the unbound account through the back door. */
    @Test @DisplayName("register — a restaurant that does not exist is refused before anything is created")
    void register_rejectsUnknownRestaurant() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@test.com"); request.setPassword("password123");
        request.setFirstName("New"); request.setLastName("User");
        request.setRestaurantId(999L);

        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(restaurantRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Restaurant not found");
        verify(userRepository, never()).save(any());
    }

    @Test @DisplayName("register — duplicate email throws")
    void register_duplicateEmail_throws() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("admin@test.com");
        when(userRepository.existsByEmail("admin@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already registered");
    }

    @Test @DisplayName("login — success returns tokens")
    void login_success() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@test.com"); request.setPassword("password");

        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken(any())).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("refresh-token");

        AuthResponse result = authService.login(request);

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        verify(authenticationManager).authenticate(any());
    }

    @Test @DisplayName("login — invalid credentials throws")
    void login_invalidCredentials_throws() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@test.com"); request.setPassword("wrong");

        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test @DisplayName("refreshToken — success issues new tokens")
    void refreshToken_success() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("valid-refresh");

        when(jwtUtil.extractUsername("valid-refresh")).thenReturn("admin@test.com");
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(user));
        when(jwtUtil.validateToken("valid-refresh", user)).thenReturn(true);
        when(jwtUtil.generateAccessToken(any())).thenReturn("new-access");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("new-refresh");

        AuthResponse result = authService.refreshToken(request);

        assertThat(result.getAccessToken()).isEqualTo("new-access");
    }

    @Test @DisplayName("refreshToken — invalid token throws")
    void refreshToken_invalid_throws() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("invalid");

        when(jwtUtil.extractUsername("invalid")).thenReturn("admin@test.com");
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(user));
        when(jwtUtil.validateToken("invalid", user)).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test @DisplayName("changePassword — success")
    void changePassword_success() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("oldpass"); request.setNewPassword("newpass123");

        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("oldpass", "$2a$encoded")).thenReturn(true);
        when(passwordEncoder.encode("newpass123")).thenReturn("$2a$newencoded");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        authService.changePassword("admin@test.com", request);

        verify(passwordEncoder).encode("newpass123");
        verify(userRepository).save(user);
    }

    @Test @DisplayName("changePassword — wrong old password throws")
    void changePassword_wrongOld_throws() {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword("wrongold"); request.setNewPassword("newpass123");

        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongold", "$2a$encoded")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword("admin@test.com", request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("incorrect");
    }

    @Test @DisplayName("forgotPassword — generates reset token")
    void forgotPassword_success() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("admin@test.com");
        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        authService.forgotPassword(request);

        verify(userRepository).save(any());
        assertThat(user.getResetToken()).isNotNull();
        assertThat(user.getResetTokenExpiry()).isNotNull();
        verify(emailService).sendPasswordReset(eq("admin@test.com"), any());
    }

    @Test @DisplayName("forgotPassword — unknown email returns quietly (no account-existence oracle)")
    void forgotPassword_unknownEmail_noOracle() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("nobody@test.com");
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        // must NOT throw (would reveal the email is unregistered) and must not touch the DB / mail
        authService.forgotPassword(request);

        verify(userRepository, never()).save(any());
        verify(emailService, never()).sendPasswordReset(any(), any());
    }

    @Test @DisplayName("login — a locked account is rejected without hitting authentication")
    void login_lockedAccount_rejected() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@test.com");
        request.setPassword("pw");
        when(loginAttemptService.isLocked("admin@test.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(org.springframework.security.authentication.LockedException.class);
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test @DisplayName("login — a bad password records a failed attempt")
    void login_badPassword_recordsFailure() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@test.com");
        request.setPassword("wrong");
        when(loginAttemptService.isLocked("admin@test.com")).thenReturn(false);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class);
        verify(loginAttemptService).recordFailure("admin@test.com");
    }

    @Test @DisplayName("resetPassword — resets with valid token")
    void resetPassword_success() {
        user.setResetToken("valid-token");
        user.setResetTokenExpiry(java.time.LocalDateTime.now().plusHours(1));
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("valid-token");
        request.setNewPassword("newpass123");
        when(userRepository.findByResetToken("valid-token")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newpass123")).thenReturn("$2a$newencoded");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        authService.resetPassword(request);

        verify(passwordEncoder).encode("newpass123");
        assertThat(user.getResetToken()).isNull();
    }
}
