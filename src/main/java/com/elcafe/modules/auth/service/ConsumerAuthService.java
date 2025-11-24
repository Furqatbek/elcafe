package com.elcafe.modules.auth.service;

import com.elcafe.modules.auth.dto.*;
import com.elcafe.modules.auth.entity.ConsumerSession;
import com.elcafe.modules.auth.entity.OtpCode;
import com.elcafe.modules.auth.repository.ConsumerSessionRepository;
import com.elcafe.modules.auth.repository.OtpCodeRepository;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.sms.dto.SendSmsRequest;
import com.elcafe.modules.sms.service.SmsService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Random;
import java.util.UUID;

/**
 * Service for consumer authentication using OTP
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumerAuthService {

    private final OtpCodeRepository otpCodeRepository;
    private final ConsumerSessionRepository sessionRepository;
    private final CustomerRepository customerRepository;
    private final SmsService smsService;

    @Value("${app.security.jwt.secret}")
    private String jwtSecret;

    @Value("${app.consumer.otp.expiration-minutes:5}")
    private Integer otpExpirationMinutes;

    @Value("${app.consumer.otp.max-attempts:3}")
    private Integer maxOtpAttempts;

    @Value("${app.consumer.session.access-token-expiration:3600000}") // 1 hour
    private Long accessTokenExpiration;

    @Value("${app.consumer.session.refresh-token-expiration:2592000000}") // 30 days
    private Long refreshTokenExpiration;

    @Value("${app.consumer.otp.rate-limit-minutes:1}")
    private Integer rateLimitMinutes;

    @Value("${app.consumer.otp.rate-limit-count:3}")
    private Integer rateLimitCount;

    @Value("${app.consumer.otp.include-in-response:false}")
    private Boolean includeOtpInResponse;

    private static final Random RANDOM = new Random();

    /**
     * Request OTP for phone number
     */
    @Transactional
    public ConsumerLoginResponse requestOtp(ConsumerLoginRequest request, String ipAddress, String userAgent) {
        String phoneNumber = normalizePhoneNumber(request.getPhoneNumber());

        // Rate limiting check
        checkRateLimit(phoneNumber);

        // Generate 6-digit OTP
        String otpCode = generateOtpCode();

        // Calculate expiration
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(otpExpirationMinutes);

        // Save OTP to database
        OtpCode otp = OtpCode.builder()
                .phoneNumber(phoneNumber)
                .otpCode(otpCode)
                .expiresAt(expiresAt)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        otpCodeRepository.save(otp);

        // Send OTP via SMS
        try {
            SendSmsRequest smsRequest = SendSmsRequest.builder()
                    .mobilePhone(phoneNumber)
                    .message(String.format("Your verification code is: %s. Valid for %d minutes.",
                            otpCode, otpExpirationMinutes))
                    .build();

            smsService.sendSms(smsRequest);
            log.info("OTP sent successfully to {}", phoneNumber);
        } catch (Exception e) {
            log.error("Failed to send OTP SMS to {}: {}", phoneNumber, e.getMessage());
            // Don't fail the request - OTP is still saved in DB
        }

        long expiresInSeconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), expiresAt);

        ConsumerLoginResponse response = ConsumerLoginResponse.builder()
                .message("OTP sent successfully")
                .phoneNumber(phoneNumber)
                .expiresAt(expiresAt)
                .expiresInSeconds(expiresInSeconds)
                .build();

        // Include OTP in response for development/testing (controlled by config)
        if (includeOtpInResponse) {
            response.setOtpCode(otpCode);
            log.warn("OTP code included in response (development mode): {}", otpCode);
        }

        return response;
    }

    /**
     * Verify OTP and create session
     */
    @Transactional
    public ConsumerAuthResponse verifyOtp(VerifyOtpRequest request, String ipAddress, String userAgent) {
        String phoneNumber = normalizePhoneNumber(request.getPhoneNumber());
        String otpCode = request.getOtpCode();

        // Find OTP
        OtpCode otp = otpCodeRepository.findByPhoneNumberAndOtpCodeAndIsVerifiedFalse(phoneNumber, otpCode)
                .orElseThrow(() -> new RuntimeException("Invalid OTP code"));

        // Check if expired
        if (otp.isExpired()) {
            throw new RuntimeException("OTP code has expired");
        }

        // Check attempts
        otp.incrementAttempts();
        if (otp.getAttempts() > maxOtpAttempts) {
            otpCodeRepository.save(otp);
            throw new RuntimeException("Maximum verification attempts exceeded");
        }

        // Mark as verified
        otp.setIsVerified(true);
        otp.setVerifiedAt(LocalDateTime.now());
        otpCodeRepository.save(otp);

        // Find or create customer
        Customer customer = customerRepository.findByPhone(phoneNumber)
                .orElse(null);

        boolean isNewUser = customer == null;

        if (isNewUser) {
            // Create new customer with placeholder name (can be updated later)
            customer = Customer.builder()
                    .phone(phoneNumber)
                    .firstName("Customer")
                    .lastName(phoneNumber.substring(Math.max(0, phoneNumber.length() - 4))) // Last 4 digits
                    .registrationSource(com.elcafe.modules.customer.enums.RegistrationSource.MOBILE_APP)
                    .build();
            customer = customerRepository.save(customer);
            log.info("Created new customer for phone number: {}", phoneNumber);
        }

        // Invalidate existing sessions
        sessionRepository.invalidateAllSessionsByPhoneNumber(phoneNumber);

        // Generate tokens
        String accessToken = generateAccessToken(phoneNumber, customer.getId());
        String refreshToken = generateRefreshToken(phoneNumber);

        // Calculate expiration times
        LocalDateTime accessExpiresAt = LocalDateTime.now().plusSeconds(accessTokenExpiration / 1000);
        LocalDateTime refreshExpiresAt = LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000);

        // Create session
        ConsumerSession session = ConsumerSession.builder()
                .phoneNumber(phoneNumber)
                .customer(customer)
                .sessionToken(accessToken)
                .refreshToken(refreshToken)
                .expiresAt(accessExpiresAt)
                .refreshExpiresAt(refreshExpiresAt)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        sessionRepository.save(session);

        long expiresInSeconds = accessTokenExpiration / 1000;

        log.info("Consumer authenticated successfully: phone={}, customerId={}, isNew={}",
                phoneNumber, customer.getId(), isNewUser);

        return ConsumerAuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresAt(accessExpiresAt)
                .expiresInSeconds(expiresInSeconds)
                .phoneNumber(phoneNumber)
                .customerId(customer.getId())
                .isNewUser(isNewUser)
                .build();
    }

    /**
     * Refresh access token using refresh token
     */
    @Transactional
    public ConsumerAuthResponse refreshAccessToken(RefreshTokenRequest request, String ipAddress, String userAgent) {
        String refreshToken = request.getRefreshToken();

        // Find session by refresh token
        ConsumerSession session = sessionRepository.findByRefreshTokenAndIsActiveTrue(refreshToken)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        // Check if refresh token expired
        if (session.isRefreshExpired()) {
            session.invalidate();
            sessionRepository.save(session);
            throw new RuntimeException("Refresh token has expired");
        }

        // Generate new access token
        String newAccessToken = generateAccessToken(session.getPhoneNumber(),
                session.getCustomer() != null ? session.getCustomer().getId() : null);

        LocalDateTime accessExpiresAt = LocalDateTime.now().plusSeconds(accessTokenExpiration / 1000);

        // Update session
        session.setSessionToken(newAccessToken);
        session.setExpiresAt(accessExpiresAt);
        session.setIpAddress(ipAddress);
        session.setUserAgent(userAgent);
        session.updateLastAccessed();

        sessionRepository.save(session);

        long expiresInSeconds = accessTokenExpiration / 1000;

        log.info("Access token refreshed for phone: {}", session.getPhoneNumber());

        return ConsumerAuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .expiresAt(accessExpiresAt)
                .expiresInSeconds(expiresInSeconds)
                .phoneNumber(session.getPhoneNumber())
                .customerId(session.getCustomer() != null ? session.getCustomer().getId() : null)
                .isNewUser(false)
                .build();
    }

    /**
     * Logout consumer (invalidate session)
     */
    @Transactional
    public void logout(String accessToken) {
        sessionRepository.findBySessionTokenAndIsActiveTrue(accessToken)
                .ifPresent(session -> {
                    session.invalidate();
                    sessionRepository.save(session);
                    log.info("Consumer logged out: phone={}", session.getPhoneNumber());
                });
    }

    /**
     * Generate 6-digit OTP code
     */
    private String generateOtpCode() {
        return String.format("%06d", RANDOM.nextInt(1000000));
    }

    /**
     * Generate JWT access token
     */
    private String generateAccessToken(String phoneNumber, Long customerId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiration);

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .setSubject(phoneNumber)
                .claim("customerId", customerId)
                .claim("type", "consumer")
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Generate refresh token (UUID-based)
     */
    private String generateRefreshToken(String phoneNumber) {
        return UUID.randomUUID().toString() + "-" + phoneNumber.hashCode();
    }

    /**
     * Normalize phone number (remove spaces, dashes, etc.)
     */
    private String normalizePhoneNumber(String phoneNumber) {
        return phoneNumber.replaceAll("[^0-9+]", "");
    }

    /**
     * Check rate limiting for OTP requests
     */
    private void checkRateLimit(String phoneNumber) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(rateLimitMinutes);
        long recentCount = otpCodeRepository.countRecentOtpsByPhoneNumber(phoneNumber, since);

        if (recentCount >= rateLimitCount) {
            throw new RuntimeException(
                    String.format("Too many OTP requests. Please wait %d minute(s) before trying again.",
                            rateLimitMinutes)
            );
        }
    }

    /**
     * Cleanup expired OTPs and sessions (scheduled task)
     * Runs every hour
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredData() {
        LocalDateTime now = LocalDateTime.now();

        try {
            otpCodeRepository.deleteExpiredOtps(now);
            log.info("Cleaned up expired OTP codes");
        } catch (Exception e) {
            log.error("Failed to cleanup expired OTPs: {}", e.getMessage());
        }

        try {
            sessionRepository.deleteExpiredSessions(now);
            log.info("Cleaned up expired consumer sessions");
        } catch (Exception e) {
            log.error("Failed to cleanup expired sessions: {}", e.getMessage());
        }
    }
}
