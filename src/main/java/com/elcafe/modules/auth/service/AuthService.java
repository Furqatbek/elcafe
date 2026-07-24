package com.elcafe.modules.auth.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ConflictException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.auth.dto.*;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.mapper.UserMapper;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import com.elcafe.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final WaiterRepository waiterRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.getEmail());

        // /api/v1/auth/register is a public (permitAll) endpoint. Never let an
        // anonymous caller self-assign an administrative role — that would be a
        // full platform takeover with no prior credentials. Admin/super-admin
        // accounts are created only via the ADMIN-gated SystemUserController or
        // a trusted seed. (Non-admin staff self-registration remains as before.)
        UserRole requestedRole = request.getRole();
        if (requestedRole == null || requestedRole == UserRole.UNKNOWN || requestedRole.isAdminLevel()) {
            log.warn("Rejected public registration attempt for {} with disallowed role {}",
                    request.getEmail(), requestedRole);
            throw new BadRequestException("This role cannot be self-assigned during registration");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already registered");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .role(request.getRole())
                .active(true)
                .emailVerified(false)
                .build();

        user = userRepository.save(user);
        log.info("User registered successfully with ID: {}", user.getId());

        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userMapper.toResponse(user))
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.getActive()) {
            throw new BadRequestException("Account is deactivated");
        }

        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        log.info("User logged in successfully: {}", user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userMapper.toResponse(user))
                .build();
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        log.info("Refreshing token");

        // A refresh token signed with an old key (e.g. after a JWT secret /
        // key-derivation change) or one that has expired makes jjwt throw an
        // unchecked JwtException. Catch it and surface a clean 400 so clients
        // re-login, rather than letting it fall through to a generic 500.
        String tokenType;
        try {
            tokenType = jwtUtil.extractClaim(request.getRefreshToken(), c -> c.get("type", String.class));
        } catch (io.jsonwebtoken.JwtException e) {
            log.warn("Refresh token could not be parsed: {}", e.getMessage());
            throw new BadRequestException("Invalid or expired refresh token");
        }

        // Waiter (mobile app) refresh tokens hit the same endpoint but are
        // backed by the Waiter table, not User. Route them to the waiter flow.
        if ("waiter_refresh".equals(tokenType)) {
            return refreshWaiterToken(request.getRefreshToken());
        }

        String email;
        try {
            email = jwtUtil.extractUsername(request.getRefreshToken());
        } catch (io.jsonwebtoken.JwtException e) {
            log.warn("Refresh token could not be parsed: {}", e.getMessage());
            throw new BadRequestException("Invalid or expired refresh token");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        boolean valid;
        try {
            valid = jwtUtil.validateToken(request.getRefreshToken(), user);
        } catch (io.jsonwebtoken.JwtException e) {
            log.warn("Refresh token validation failed: {}", e.getMessage());
            throw new BadRequestException("Invalid or expired refresh token");
        }
        if (!valid) {
            throw new BadRequestException("Invalid refresh token");
        }

        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userMapper.toResponse(user))
                .build();
    }

    /**
     * Re-issue a waiter access + refresh token pair from a valid waiter
     * refresh token. The waiter is reloaded by id (carried in the token) so a
     * deactivated account can't keep refreshing. The response uses the same
     * {@code {accessToken, refreshToken}} shape as the user flow, which the
     * mobile app already consumes — no client change needed.
     */
    private AuthResponse refreshWaiterToken(String refreshToken) {
        String identifier;
        Long waiterId;
        try {
            identifier = jwtUtil.extractUsername(refreshToken);
            Object rawId = jwtUtil.extractClaim(refreshToken, c -> c.get("waiterId"));
            waiterId = rawId instanceof Number ? ((Number) rawId).longValue() : null;
        } catch (io.jsonwebtoken.JwtException e) {
            log.warn("Waiter refresh token could not be parsed: {}", e.getMessage());
            throw new BadRequestException("Invalid or expired refresh token");
        }
        if (waiterId == null) {
            throw new BadRequestException("Invalid refresh token");
        }

        Waiter waiter = waiterRepository.findById(waiterId)
                .orElseThrow(() -> new BadRequestException("Waiter not found"));
        if (!Boolean.TRUE.equals(waiter.getActive())) {
            throw new BadRequestException("Waiter account is inactive");
        }

        String newAccess = jwtUtil.generateWaiterAccessToken(
                identifier, waiter.getId(), waiter.getRole().name());
        String newRefresh = jwtUtil.generateWaiterRefreshToken(identifier, waiter.getId());

        return AuthResponse.builder()
                .accessToken(newAccess)
                .refreshToken(newRefresh)
                .build();
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        log.info("Password reset requested for email: {}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        String resetToken = UUID.randomUUID().toString();
        user.setResetToken(resetToken);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(24));

        userRepository.save(user);

        // TODO: Send email with reset token
        log.info("Password reset token generated for user: {}", user.getEmail());
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        log.info("Password change requested for user: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed successfully for user: {}", email);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        log.info("Password reset attempt with token");

        User user = userRepository.findByResetToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Invalid reset token"));

        if (user.getResetTokenExpiry() == null || user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Reset token has expired");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);

        userRepository.save(user);
        log.info("Password reset successfully for user: {}", user.getEmail());
    }
}
