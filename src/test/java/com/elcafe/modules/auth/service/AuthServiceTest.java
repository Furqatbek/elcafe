package com.elcafe.modules.auth.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ConflictException;
import com.elcafe.modules.auth.dto.*;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.mapper.UserMapper;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.enums.WaiterRole;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import com.elcafe.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private WaiterRepository waiterRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserMapper userMapper;
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

    @Test @DisplayName("register — success with JWT tokens")
    void register_success() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@test.com"); request.setPassword("password123");
        request.setFirstName("New"); request.setLastName("User"); request.setRole(UserRole.OPERATOR);

        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> { User u = i.getArgument(0); u.setId(2L); return u; });
        when(jwtUtil.generateAccessToken(any())).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("refresh-token");

        AuthResponse result = authService.register(request);

        assertThat(result.getAccessToken()).isEqualTo("access-token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
        verify(passwordEncoder).encode("password123");
    }

    @Test @DisplayName("register — rejects self-assigning ADMIN (privilege escalation)")
    void register_rejectsAdminRole() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("attacker@test.com"); request.setPassword("password123");
        request.setFirstName("A"); request.setLastName("B"); request.setRole(UserRole.ADMIN);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be self-assigned");
        verify(userRepository, never()).save(any());
    }

    @Test @DisplayName("register — rejects self-assigning SUPER_ADMIN")
    void register_rejectsSuperAdminRole() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("attacker@test.com"); request.setPassword("password123");
        request.setFirstName("A"); request.setLastName("B"); request.setRole(UserRole.SUPER_ADMIN);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("cannot be self-assigned");
        verify(userRepository, never()).save(any());
    }

    @Test @DisplayName("register — rejects UNKNOWN role")
    void register_rejectsUnknownRole() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("x@test.com"); request.setPassword("password123");
        request.setFirstName("A"); request.setLastName("B"); request.setRole(UserRole.UNKNOWN);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any());
    }

    @Test @DisplayName("register — duplicate email throws")
    void register_duplicateEmail_throws() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("admin@test.com");
        request.setRole(UserRole.OPERATOR); // valid non-admin role so it reaches the dup-email check
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

    @Test @DisplayName("refreshToken — unparseable token (old key/expired) yields 400, not 500")
    void refreshToken_jwtException_yields400() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("signed-with-old-key");

        // Simulates the mass key-invalidation at deploy: jjwt throws an
        // unchecked JwtException on parse. Must surface as BadRequest, not 500.
        when(jwtUtil.extractUsername("signed-with-old-key"))
                .thenThrow(new io.jsonwebtoken.security.SignatureException("bad MAC"));

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Invalid or expired refresh token");
    }

    @Test @DisplayName("refreshToken — waiter refresh token reissues waiter tokens, not user tokens")
    void refreshToken_waiterToken_reissuesWaiterTokens() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("waiter-refresh");

        Waiter waiter = Waiter.builder()
                .id(9L).name("W").active(true).role(WaiterRole.WAITER).build();

        // extractClaim is called twice: first for "type", then for "waiterId".
        when(jwtUtil.extractClaim(eq("waiter-refresh"), any())).thenReturn("waiter_refresh", 9L);
        when(jwtUtil.extractUsername("waiter-refresh")).thenReturn("waiter_9");
        when(waiterRepository.findById(9L)).thenReturn(Optional.of(waiter));
        when(jwtUtil.generateWaiterAccessToken("waiter_9", 9L, "WAITER")).thenReturn("new-waiter-access");
        when(jwtUtil.generateWaiterRefreshToken("waiter_9", 9L)).thenReturn("new-waiter-refresh");

        AuthResponse result = authService.refreshToken(request);

        assertThat(result.getAccessToken()).isEqualTo("new-waiter-access");
        assertThat(result.getRefreshToken()).isEqualTo("new-waiter-refresh");
        assertThat(result.getUser()).isNull();
        // must never touch the User table for a waiter token
        verify(userRepository, never()).findByEmail(anyString());
    }

    @Test @DisplayName("refreshToken — deactivated waiter cannot refresh")
    void refreshToken_inactiveWaiter_throws() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("waiter-refresh");

        Waiter waiter = Waiter.builder()
                .id(9L).name("W").active(false).role(WaiterRole.WAITER).build();

        when(jwtUtil.extractClaim(eq("waiter-refresh"), any())).thenReturn("waiter_refresh", 9L);
        when(jwtUtil.extractUsername("waiter-refresh")).thenReturn("waiter_9");
        when(waiterRepository.findById(9L)).thenReturn(Optional.of(waiter));

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("inactive");
        verify(jwtUtil, never()).generateWaiterAccessToken(anyString(), any(), anyString());
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
