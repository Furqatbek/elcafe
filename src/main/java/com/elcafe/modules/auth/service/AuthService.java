package com.elcafe.modules.auth.service;

import com.elcafe.common.security.UserTenantBinding;
import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ConflictException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.auth.dto.*;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.mapper.UserMapper;
import com.elcafe.modules.auth.repository.UserRepository;
import com.elcafe.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
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
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserMapper userMapper;
    private final LoginAttemptService loginAttemptService;
    private final EmailService emailService;
    /** Provisioning must name a real restaurant — a dangling id would recreate the unbound account. */
    private final com.elcafe.modules.restaurant.repository.RestaurantRepository restaurantRepository;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Registering new user with email: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email already registered");
        }

        // SECURITY: never trust a client-supplied role. This endpoint (SUPER_ADMIN-only since public
        // self-registration was closed) always creates an unprivileged OWNER. Privileged accounts
        // (ADMIN / SUPER_ADMIN / staff) are created only via SystemUserController / OperatorController.
        //
        // The owner is now BOUND to a restaurant. Previously no binding was set at all, so every single
        // account this endpoint produced had restaurant_id NULL — and since OWNER is tenant-scoped, that
        // is the deny-all sentinel: the account signed in perfectly and then showed an empty
        // application, which reads as a wiped database rather than a half-finished provisioning step.
        if (!restaurantRepository.existsById(request.getRestaurantId())) {
            throw new BadRequestException("Restaurant not found: " + request.getRestaurantId());
        }
        UserTenantBinding.require(UserRole.OWNER, request.getRestaurantId(), "Cannot register this owner");

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .role(UserRole.OWNER)
                .active(true)
                .emailVerified(false)
                .restaurantId(request.getRestaurantId())
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
        String email = request.getEmail();
        log.info("Login attempt for email: {}", email);

        // Per-account brute-force lockout (complements the per-IP @RateLimited(AUTH) on the endpoint).
        if (loginAttemptService.isLocked(email)) {
            long mins = (loginAttemptService.secondsUntilUnlock(email) + 59) / 60;
            throw new LockedException("Account temporarily locked due to too many failed login attempts. "
                    + "Try again in about " + Math.max(1, mins) + " minute(s).");
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            email,
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException e) {
            loginAttemptService.recordFailure(email);
            throw e; // GlobalExceptionHandler maps this to 401
        }
        loginAttemptService.recordSuccess(email);

        User user = userRepository.findByEmail(email)
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

        String email = jwtUtil.extractUsername(request.getRefreshToken());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!jwtUtil.validateToken(request.getRefreshToken(), user)) {
            throw new BadRequestException("Invalid refresh token");
        }
        // A deactivated account must not mint fresh tokens (login checks this too; the tokenVersion
        // bump on deactivate already invalidates the refresh token — this is belt-and-braces).
        if (!Boolean.TRUE.equals(user.getActive())) {
            throw new BadRequestException("Account is inactive");
        }

        String accessToken = jwtUtil.generateAccessToken(user);
        String refreshToken = jwtUtil.generateRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .user(userMapper.toResponse(user))
                .build();
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        log.info("Password reset requested");

        // Do NOT reveal whether the email is registered — return quietly for unknown emails so this
        // endpoint is not an account-existence oracle. The controller always returns a generic message.
        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null) {
            return;
        }

        String resetToken = UUID.randomUUID().toString();
        user.setResetToken(resetToken);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(24));

        userRepository.save(user);

        // Deliver the reset link by email. When SMTP is not configured, EmailService logs a warning and
        // no email is sent (the flow is honest — it does not silently pretend to have delivered).
        emailService.sendPasswordReset(user.getEmail(), resetToken);
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
        // §3.5: invalidate every existing token for this user (force re-login on all devices).
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);
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
        // §3.5: invalidate every existing token for this user.
        user.setTokenVersion((user.getTokenVersion() == null ? 0 : user.getTokenVersion()) + 1);

        userRepository.save(user);
        log.info("Password reset successfully for user: {}", user.getEmail());
    }
}
