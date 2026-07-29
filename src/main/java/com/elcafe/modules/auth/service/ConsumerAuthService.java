package com.elcafe.modules.auth.service;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.RateLimitExceededException;
import com.elcafe.exception.UnauthorizedException;
import com.elcafe.utils.LogSanitizer;
import com.elcafe.modules.auth.dto.*;
import com.elcafe.modules.auth.entity.ConsumerSession;
import com.elcafe.modules.auth.entity.OtpCode;
import com.elcafe.modules.auth.repository.ConsumerSessionRepository;
import com.elcafe.modules.auth.repository.OtpCodeRepository;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.enums.RegistrationSource;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.loyalty.service.LoyaltyService;
import com.elcafe.modules.telegram.entity.TelegramBotConfig;
import com.elcafe.modules.telegram.entity.TelegramSubscriber;
import com.elcafe.modules.telegram.entity.TelegramSubscriberLocation;
import com.elcafe.modules.telegram.repository.TelegramBotConfigRepository;
import com.elcafe.modules.telegram.repository.TelegramSubscriberLocationRepository;
import com.elcafe.modules.telegram.repository.TelegramSubscriberRepository;
import com.elcafe.modules.telegram.service.TelegramInitDataValidator;

import java.math.BigDecimal;
import com.elcafe.modules.sms.dto.SendSmsRequest;
import com.elcafe.modules.sms.service.SmsService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.security.SecureRandom;
import java.util.Date;
import java.util.List;
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
    /** V182 welcome bonus. @Lazy: loyalty reaches back into customer/order services, and this keeps a
     *  future dependency edge from turning into a startup cycle in the auth path. */
    @org.springframework.context.annotation.Lazy
    private final LoyaltyService loyaltyService;
    private final SmsService smsService;
    // Telegram Mini App login: the bot config supplies the signing token, the validator verifies the
    // signed initData, and the subscriber row carries the wizard-verified phone we key the customer on.
    private final TelegramBotConfigRepository telegramBotConfigRepository;
    private final TelegramSubscriberRepository telegramSubscriberRepository;
    private final TelegramSubscriberLocationRepository telegramSubscriberLocationRepository;
    private final TelegramInitDataValidator telegramInitDataValidator;

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

    /**
     * MUST be a CSPRNG. The OTP <em>is</em> the login credential here (consumer sign-in is
     * passwordless), so a predictable code is a full account takeover — and it defeats the attempt
     * limiter entirely, because an attacker who can predict the code never guesses wrong.
     * {@link java.util.Random} was used here and is a 48-bit LCG: requesting a handful of codes to a
     * phone you control leaks enough state to recover the seed and compute anyone else's next OTP.
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Request OTP for phone number
     */
    @Transactional
    public ConsumerLoginResponse requestOtp(ConsumerLoginRequest request, String ipAddress, String userAgent) {
        String phoneNumber = normalizePhoneNumber(request.getPhoneNumber());
        Long restaurantId = request.getRestaurantId();

        // Do NOT log customer PII (phone, name, birth date) — logs are shipped/retained. Log only the
        // non-identifying context needed to trace a request.
        log.debug("Consumer login request - restaurantId: {}, source: {}, language: {}",
                restaurantId, request.getRegistrationSource(), request.getLanguage());

        // Rate limiting check
        checkRateLimit(phoneNumber);

        // Find or create the customer for THIS restaurant (V150: identity is per-restaurant).
        Customer customer = customerRepository.findByPhoneAndRestaurantId(phoneNumber, restaurantId)
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
                    .restaurantId(restaurantId)
                    .phone(phoneNumber)
                    .firstName(firstName)
                    .lastName(lastName)
                    .birthDate(request.getBirthDate())
                    .language(request.getLanguage())
                    .registrationSource(request.getRegistrationSource())
                    .build();
            customer = customerRepository.save(customer);
            log.info("Created new customer - id: {}, phone: {}, source: {}, language: {}",
                    customer.getId(), LogSanitizer.phone(phoneNumber), request.getRegistrationSource(), request.getLanguage());
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
                log.info("Updated customer during login request: phone={}", LogSanitizer.phone(phoneNumber));
            }
        }

        // Generate 6-digit OTP
        String otpCode = generateOtpCode();

        // Never log the OTP code or phone number in production — logs are shipped/retained and a live
        // OTP + phone is an account-takeover primitive. The actual code is printed only in dev mode.
        if (developmentMode) {
            log.info("[dev] OTP for {}: {}", LogSanitizer.phone(phoneNumber), otpCode);
        } else {
            log.debug("OTP generated and dispatched");
        }

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
                log.info("OTP sent successfully to {}", LogSanitizer.phone(phoneNumber));
            } catch (Exception e) {
                log.error("Failed to send OTP SMS to {}: {}", LogSanitizer.phone(phoneNumber), e.getMessage());
                // Don't fail the request - OTP is still saved in DB
            }
        } else {
            log.info("Development mode: SMS sending skipped for {}", LogSanitizer.phone(phoneNumber));
        }

        long expiresInSeconds = ChronoUnit.SECONDS.between(LocalDateTime.now(), expiresAt);

        ConsumerLoginResponse response = ConsumerLoginResponse.builder()
                .message("OTP sent successfully")
                .phoneNumber(phoneNumber)
                .expiresAt(expiresAt)
                .expiresInSeconds(expiresInSeconds)
                .build();

        // Include OTP in response for development/testing (controlled by config; forced false in prod).
        if (includeOtpInResponse) {
            response.setOtpCode(otpCode);
            log.warn("OTP code included in response (dev/test config is ON)");
        }

        return response;
    }

    /**
     * Verify OTP and create session
     */
    @Transactional
    public ConsumerAuthResponse verifyOtp(VerifyOtpRequest request, String ipAddress, String userAgent) {
        String phoneNumber = normalizePhoneNumber(request.getPhoneNumber());
        Long restaurantId = request.getRestaurantId();
        String otpCode = request.getOtpCode();

        OtpCode otp;

        // In development mode, accept any OTP code
        if (developmentMode) {
            log.info("Development mode: Accepting any OTP code for {}", LogSanitizer.phone(phoneNumber));
            // Find any recent OTP for this phone number (to mark as verified)
            otp = otpCodeRepository.findByPhoneNumberAndOtpCodeAndIsVerifiedFalse(phoneNumber, otpCode)
                    .orElseGet(() -> {
                        // If no matching OTP found in dev mode, create a temporary one
                        log.info("Development mode: Creating temporary OTP record for {}", LogSanitizer.phone(phoneNumber));
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
            // Production mode: strict OTP validation.
            // Fetch the phone's active OTP WITHOUT filtering by the submitted code, then compare in
            // application code — otherwise a wrong guess returns no row and the attempt counter never
            // increments, so the max-attempts cap can't fire and the code is brute-forceable.
            otp = otpCodeRepository.findLatestValidOtp(phoneNumber, LocalDateTime.now())
                    .orElseThrow(() -> new UnauthorizedException("No active OTP. Please request a new code."));

            if (!java.security.MessageDigest.isEqual(
                    otp.getOtpCode().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    otpCode == null ? new byte[0] : otpCode.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                // Wrong code: count the attempt against the phone's active OTP; retire it once exhausted.
                otp.incrementAttempts();
                otpCodeRepository.save(otp);
                if (otp.getAttempts() >= maxOtpAttempts) {
                    otp.setIsVerified(true); // burn it so it can no longer be guessed
                    otpCodeRepository.save(otp);
                    throw new BadRequestException("Maximum verification attempts exceeded. Request a new code.");
                }
                throw new UnauthorizedException("Invalid OTP code");
            }

            if (otp.getAttempts() >= maxOtpAttempts) {
                throw new BadRequestException("Maximum verification attempts exceeded. Request a new code.");
            }
        }

        // Mark as verified
        otp.setIsVerified(true);
        otp.setVerifiedAt(LocalDateTime.now());
        otpCodeRepository.save(otp);

        // Find the per-restaurant customer (created during the matching login request).
        Customer customer = customerRepository.findByPhoneAndRestaurantId(phoneNumber, restaurantId)
                .orElseThrow(() -> new BadRequestException("Customer not found. Please request OTP first."));

        log.info("Customer authenticated: restaurantId={}, customerId={}", restaurantId, customer.getId());

        // V182 welcome bonus — deliberately here and not in the login request that CREATED this customer
        // row. Registration is only complete once the code is accepted, so a guest who asks for an OTP
        // and never enters it earns nothing. Safe to call on every verify: the grant is idempotent per
        // customer, so a returning guest signing in again credits zero. Best-effort — a loyalty failure
        // must never cost the customer their login.
        BigDecimal registrationBonusGranted = BigDecimal.ZERO;
        try {
            registrationBonusGranted = loyaltyService.grantRegistrationBonus(customer.getId(), restaurantId);
        } catch (Exception e) {
            log.error("Registration bonus failed for customer {} — login continues", customer.getId(), e);
        }

        log.info("Consumer authenticated successfully: phone={}, customerId={}",
                LogSanitizer.phone(phoneNumber), customer.getId());

        return issueConsumerSession(customer, phoneNumber, ipAddress, userAgent, registrationBonusGranted);
    }

    /**
     * Authenticate a Telegram Mini App visitor. The bot token signs {@code initData}, so a passing
     * verification proves both the tenant (the token belongs to one restaurant) and the Telegram user —
     * neither is client-asserted. On success this issues the exact same consumer session the OTP flow
     * does, so the Mini App orders through {@code /consumer/**} like any website visitor.
     *
     * <p>Ordering is keyed to a phone-backed {@link Customer}. The bot's "Share contact" wizard step
     * captures a Telegram-VERIFIED phone; if the visitor skipped it there is no trustworthy phone to key
     * on, so rather than accept a hand-typed number we return {@code registrationRequired} and let the
     * Mini App bounce them to that one-tap step in the bot.
     */
    @Transactional
    public TelegramMiniAppAuthResponse authenticateViaTelegram(TelegramMiniAppAuthRequest request,
                                                               String ipAddress, String userAgent) {
        Long restaurantId = request.getRestaurantId();

        // The restaurant's active bot token is the signing key — no bot, nothing could have signed this.
        TelegramBotConfig config = telegramBotConfigRepository.findByRestaurantIdAndIsActiveTrue(restaurantId)
                .orElseThrow(() -> new BadRequestException("This restaurant has no active Telegram bot"));

        // Verify signature + freshness against THAT bot; throws 401 on any mismatch or staleness.
        TelegramInitDataValidator.ValidatedTelegramUser tgUser =
                telegramInitDataValidator.validate(request.getInitData(), config.getBotToken());

        // Upsert the subscriber: capture the Mini-App visitor on the marketing list, and read the phone
        // the bot wizard may already have verified.
        TelegramSubscriber subscriber = telegramSubscriberRepository
                .findByTelegramUserIdAndRestaurantId(tgUser.telegramUserId(), restaurantId)
                .orElseGet(() -> TelegramSubscriber.builder()
                        .restaurantId(restaurantId)
                        .telegramUserId(tgUser.telegramUserId())
                        .subscribedAt(OffsetDateTime.now(ZoneOffset.UTC))
                        .isActive(true)
                        .isBlocked(false)
                        .build());
        if (tgUser.username() != null) subscriber.setUsername(tgUser.username());
        if (tgUser.firstName() != null) subscriber.setFirstName(tgUser.firstName());
        if (tgUser.lastName() != null) subscriber.setLastName(tgUser.lastName());
        if (tgUser.languageCode() != null) subscriber.setLanguageCode(tgUser.languageCode());
        subscriber.setLastInteractionAt(OffsetDateTime.now(ZoneOffset.UTC));

        String phone = subscriber.getPhone() == null ? null : normalizePhoneNumber(subscriber.getPhone());
        if (phone == null || phone.replaceAll("[^0-9]", "").length() < 7) {
            telegramSubscriberRepository.save(subscriber);
            log.info("Telegram Mini App auth: telegram user has no verified phone; registration required "
                    + "(restaurantId={})", restaurantId);
            return TelegramMiniAppAuthResponse.registrationRequired();
        }

        // Resolve or create the per-restaurant customer (V150: identity is per-restaurant), mirroring the
        // OTP path's create-with-defaults.
        Customer customer = customerRepository.findByPhoneAndRestaurantId(phone, restaurantId)
                .orElseGet(() -> customerRepository.save(Customer.builder()
                        .restaurantId(restaurantId)
                        .phone(phone)
                        .firstName(firstNameOrDefault(tgUser))
                        .lastName(lastNameOrDefault(tgUser, phone))
                        .language(tgUser.languageCode())
                        .registrationSource(RegistrationSource.TELEGRAM_BOT)
                        .build()));

        if (subscriber.getCustomer() == null) {
            subscriber.setCustomer(customer);
        }
        telegramSubscriberRepository.save(subscriber);

        // Best-effort welcome bonus — idempotent per customer, and a loyalty failure must never cost the
        // visitor their login (same contract as verifyOtp).
        BigDecimal registrationBonusGranted = BigDecimal.ZERO;
        try {
            registrationBonusGranted = loyaltyService.grantRegistrationBonus(customer.getId(), restaurantId);
        } catch (Exception e) {
            log.error("Registration bonus failed for customer {} — login continues", customer.getId(), e);
        }

        log.info("Telegram Mini App auth success: restaurantId={}, customerId={}", restaurantId, customer.getId());
        TelegramMiniAppAuthResponse.Prefill prefill = buildPrefill(customer, subscriber);
        return TelegramMiniAppAuthResponse.authenticated(
                issueConsumerSession(customer, phone, ipAddress, userAgent, registrationBonusGranted), prefill);
    }

    /**
     * Checkout prefill for the Mini App: the customer's known contact plus their default saved delivery
     * pin (the location shared during the bot wizard), so a returning customer isn't retyping it. Falls
     * back to any saved location, then to no pin at all.
     */
    private TelegramMiniAppAuthResponse.Prefill buildPrefill(Customer customer, TelegramSubscriber subscriber) {
        Double latitude = null;
        Double longitude = null;
        List<TelegramSubscriberLocation> locations = telegramSubscriberLocationRepository.findAllBySubscriber(subscriber);
        TelegramSubscriberLocation pin = locations.stream()
                .filter(l -> Boolean.TRUE.equals(l.getIsDefault()))
                .findFirst()
                .orElse(locations.isEmpty() ? null : locations.get(0));
        if (pin != null) {
            latitude = pin.getLatitude();
            longitude = pin.getLongitude();
        }
        return TelegramMiniAppAuthResponse.Prefill.builder()
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .phone(customer.getPhone())
                .latitude(latitude)
                .longitude(longitude)
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
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));

        // Check if refresh token expired
        if (session.isRefreshExpired()) {
            session.invalidate();
            sessionRepository.save(session);
            throw new BadRequestException("Refresh token has expired");
        }

        // Generate new access token, preserving the customer's restaurant binding.
        Customer sessionCustomer = session.getCustomer();
        String newAccessToken = generateAccessToken(
                session.getPhoneNumber(),
                sessionCustomer != null ? sessionCustomer.getId() : null,
                sessionCustomer != null ? sessionCustomer.getRestaurantId() : null);

        LocalDateTime accessExpiresAt = LocalDateTime.now().plusSeconds(accessTokenExpiration / 1000);

        // Update session
        session.setSessionToken(newAccessToken);
        session.setExpiresAt(accessExpiresAt);
        session.setIpAddress(ipAddress);
        session.setUserAgent(userAgent);
        session.updateLastAccessed();

        sessionRepository.save(session);

        long expiresInSeconds = accessTokenExpiration / 1000;

        log.info("Access token refreshed for phone: {}", LogSanitizer.phone(session.getPhoneNumber()));

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
                    log.info("Consumer logged out: phone={}", LogSanitizer.phone(session.getPhoneNumber()));
                });
    }

    /**
     * Generate 6-digit OTP code
     */
    private String generateOtpCode() {
        return String.format("%06d", RANDOM.nextInt(1000000));
    }

    /**
     * Generate JWT access token. The {@code restaurantId} claim binds the consumer session to its
     * tenant so {@code JwtAuthenticationFilter} can populate {@link com.elcafe.common.tenant.TenantContext}
     * and the §3.4 backstop scopes the request's queries.
     */
    private String generateAccessToken(String phoneNumber, Long customerId, Long restaurantId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiration);

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .setSubject(phoneNumber)
                .claim("customerId", customerId)
                .claim("restaurantId", restaurantId)
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
     * Issue a fresh consumer session for an already-resolved customer: invalidate prior sessions, mint the
     * access/refresh tokens (the access token carries the restaurant so requests are tenant-scoped),
     * persist the session, and shape the response. Shared by the OTP and Telegram Mini App logins so both
     * mint an identical token — {@code JwtAuthenticationFilter} and every {@code /consumer} endpoint then
     * treat them the same. {@code registrationBonusGranted} is non-zero only on the login that completed a
     * first registration (the grant is idempotent per customer), so the menu can show what was earned.
     */
    private ConsumerAuthResponse issueConsumerSession(Customer customer, String phoneNumber,
                                                      String ipAddress, String userAgent,
                                                      BigDecimal registrationBonusGranted) {
        // Scoped to the customer (i.e. this restaurant's record), not the phone.
        sessionRepository.invalidateAllSessionsByCustomerId(customer.getId());

        String accessToken = generateAccessToken(phoneNumber, customer.getId(), customer.getRestaurantId());
        String refreshToken = generateRefreshToken(phoneNumber);

        LocalDateTime accessExpiresAt = LocalDateTime.now().plusSeconds(accessTokenExpiration / 1000);
        LocalDateTime refreshExpiresAt = LocalDateTime.now().plusSeconds(refreshTokenExpiration / 1000);

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

        return ConsumerAuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresAt(accessExpiresAt)
                .expiresInSeconds(accessTokenExpiration / 1000)
                .phoneNumber(phoneNumber)
                .customerId(customer.getId())
                .isNewUser(false)
                .registrationBonusGranted(registrationBonusGranted)
                .build();
    }

    private static String firstNameOrDefault(TelegramInitDataValidator.ValidatedTelegramUser u) {
        return (u.firstName() != null && !u.firstName().isBlank()) ? u.firstName() : "Customer";
    }

    private static String lastNameOrDefault(TelegramInitDataValidator.ValidatedTelegramUser u, String phone) {
        if (u.lastName() != null && !u.lastName().isBlank()) {
            return u.lastName();
        }
        return phone.substring(Math.max(0, phone.length() - 4)); // last 4 digits, same as the OTP path
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
            throw new RateLimitExceededException(
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
    @SchedulerLock(name = "consumer-auth-expired-cleanup", lockAtLeastFor = "PT30S")
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
