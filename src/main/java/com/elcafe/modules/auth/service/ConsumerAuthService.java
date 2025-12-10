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

    @Value("${app.consumer.otp.development-mode:false}")
    private Boolean developmentMode;

    private static final Random RANDOM = new Random();

    /**
     * Request OTP for phone number
     */
    @Transactional
    public ConsumerLoginResponse requestOtp(ConsumerLoginRequest request, String ipAddress, String userAgent) {
        String phoneNumber = normalizePhoneNumber(request.getPhoneNumber());

        // Debug logging to see what data is received
        log.info("Login request received - phone: {}, firstName: {}, lastName: {}, birthDate: {}, source: {}, language: {}",
                phoneNumber,
                request.getFirstName(),
                request.getLastName(),
                request.getBirthDate(),
                request.getRegistrationSource(),
                request.getLanguage());

        // Rate limiting check
        checkRateLimit(phoneNumber);

        // Find or create customer with provided registration data
        Customer customer = customerRepository.findByPhone(phoneNumber)
                .orElse(null);

        boolean isNewCustomer = customer == null;

        if (isNewCustomer) {
            // Create new customer with registration data
            String firstName = request.getFirstName();
            String lastName = request.getLastName();

            // Use defaults if name not provided
            if (firstName == null || firstName.trim().isEmpty()) {
                firstName = "Customer";
            }
            if (lastName == null || lastName.trim().isEmpty()) {
                lastName = phoneNumber.substring(Math.max(0, phoneNumber.length() - 4)); // Last 4 digits
            }

            customer = Customer.builder()
                    .phone(phoneNumber)
                    .firstName(firstName)
                    .lastName(lastName)
                    .birthDate(request.getBirthDate())
                    .language(request.getLanguage())
                    .registrationSource(request.getRegistrationSource())
                    .build();
            customer = customerRepository.save(customer);
            log.info("Created new customer - phone: {}, firstName: {}, lastName: {}, birthDate: {}, source: {}, language: {}",
                    phoneNumber, firstName, lastName, request.getBirthDate(), request.getRegistrationSource(), request.getLanguage());
        } else {
            // Update existing customer with new data if provided
            boolean updated = false;

            if (request.getFirstName() != null && !request.getFirstName().trim().isEmpty()) {
                customer.setFirstName(request.getFirstName());
                updated = true;
            }
            if (request.getLastName() != null && !request.getLastName().trim().isEmpty()) {
                customer.setLastName(request.getLastName());
                updated = true;
            }
            if (request.getBirthDate() != null) {
                customer.setBirthDate(request.getBirthDate());
                updated = true;
            }
            if (request.getLanguage() != null) {
                customer.setLanguage(request.getLanguage());
                updated = true;
            }
            if (request.getRegistrationSource() != null) {
                customer.setRegistrationSource(request.getRegistrationSource());
                updated = true;
            }

            if (updated) {
                customer = customerRepository.save(customer);
                log.info("Updated customer during login request: phone={}", phoneNumber);
            }
        }

        // Generate 6-digit OTP
        String otpCode = generateOtpCode();

        // Always log OTP to console for development
        log.info("=================================================");
        log.info("OTP CODE GENERATED for {}: {}", phoneNumber, otpCode);
        log.info("=================================================");

        // Calculate expiration
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(otpExpirationMinutes);

        // Save OTP to database with registration data
        OtpCode otp = OtpCode.builder()
                .phoneNumber(phoneNumber)
                .otpCode(otpCode)
                .expiresAt(expiresAt)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                // Store registration data for later use
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .birthDate(request.getBirthDate())
                .registrationSource(request.getRegistrationSource())
                .language(request.getLanguage())
                .build();

        otpCodeRepository.save(otp);

        // Send OTP via SMS (skip in development mode)
        if (!developmentMode) {
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
        } else {
            log.info("Development mode: SMS sending skipped for {}", phoneNumber);
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

        OtpCode otp;

        // In development mode, accept any OTP code
        if (developmentMode) {
            log.info("Development mode: Accepting any OTP code for {}", phoneNumber);
            // Find any recent OTP for this phone number (to mark as verified)
            otp = otpCodeRepository.findByPhoneNumberAndOtpCodeAndIsVerifiedFalse(phoneNumber, otpCode)
                    .orElseGet(() -> {
                        // If no matching OTP found in dev mode, create a temporary one
                        log.info("Development mode: Creating temporary OTP record for {}", phoneNumber);
                        OtpCode tempOtp = OtpCode.builder()
                                .phoneNumber(phoneNumber)
                                .otpCode(otpCode)
                                .expiresAt(LocalDateTime.now().plusMinutes(otpExpirationMinutes))
                                .ipAddress(ipAddress)
                                .userAgent(userAgent)
                                .build();
                        return otpCodeRepository.save(tempOtp);
                    });
        } else {
            // Production mode: strict OTP validation
            otp = otpCodeRepository.findByPhoneNumberAndOtpCodeAndIsVerifiedFalse(phoneNumber, otpCode)
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
        }

        // Mark as verified
        otp.setIsVerified(true);
        otp.setVerifiedAt(LocalDateTime.now());
        otpCodeRepository.save(otp);

        // Find customer (should have been created during login request)
        Customer customer = customerRepository.findByPhone(phoneNumber)
                .orElseThrow(() -> new RuntimeException("Customer not found. Please request OTP first."));

        log.info("Customer authenticated: phone={}, customerId={}", phoneNumber, customer.getId());

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

        log.info("Consumer authenticated successfully: phone={}, customerId={}",
                phoneNumber, customer.getId());

        return ConsumerAuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresAt(accessExpiresAt)
                .expiresInSeconds(expiresInSeconds)
                .phoneNumber(phoneNumber)
                .customerId(customer.getId())
                .isNewUser(false) // Customer was created during login request
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
