package com.elcafe.modules.auth.service;

import com.elcafe.modules.auth.dto.*;
import com.elcafe.modules.auth.entity.ConsumerSession;
import com.elcafe.modules.auth.entity.OtpCode;
import com.elcafe.modules.auth.repository.ConsumerSessionRepository;
import com.elcafe.modules.auth.repository.OtpCodeRepository;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.customer.enums.RegistrationSource;
import com.elcafe.modules.customer.repository.CustomerRepository;
import com.elcafe.modules.sms.service.SmsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsumerAuthServiceTest {

    @Mock private OtpCodeRepository otpCodeRepository;
    @Mock private ConsumerSessionRepository sessionRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private SmsService smsService;
    @InjectMocks private ConsumerAuthService consumerAuthService;

    private Customer customer;

    @BeforeEach
    void setUp() {
        // Set @Value fields via reflection
        ReflectionTestUtils.setField(consumerAuthService, "jwtSecret", "test-secret-key-that-is-at-least-32-characters-long-for-hs256");
        ReflectionTestUtils.setField(consumerAuthService, "otpExpirationMinutes", 5);
        ReflectionTestUtils.setField(consumerAuthService, "maxOtpAttempts", 3);
        ReflectionTestUtils.setField(consumerAuthService, "accessTokenExpiration", 3600000L);
        ReflectionTestUtils.setField(consumerAuthService, "refreshTokenExpiration", 2592000000L);
        ReflectionTestUtils.setField(consumerAuthService, "rateLimitMinutes", 1);
        ReflectionTestUtils.setField(consumerAuthService, "rateLimitCount", 3);
        ReflectionTestUtils.setField(consumerAuthService, "includeOtpInResponse", false);
        ReflectionTestUtils.setField(consumerAuthService, "developmentMode", false);

        customer = Customer.builder()
                .id(1L).phone("+998901234567")
                .firstName("Test").lastName("Customer")
                .active(true).build();
    }

    @Test @DisplayName("requestOtp — new customer creates account and sends OTP")
    void requestOtp_newCustomer_createsAccount() {
        ConsumerLoginRequest request = new ConsumerLoginRequest();
        request.setPhoneNumber("+998901234567");
        request.setRegistrationSource(RegistrationSource.MOBILE_APP);

        when(otpCodeRepository.countRecentOtpsByPhoneNumber(anyString(), any())).thenReturn(0L);
        when(customerRepository.findByPhone("+998901234567")).thenReturn(Optional.empty());
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> { Customer c = i.getArgument(0); c.setId(1L); return c; });
        when(otpCodeRepository.save(any(OtpCode.class))).thenAnswer(i -> i.getArgument(0));

        ConsumerLoginResponse result = consumerAuthService.requestOtp(request, "127.0.0.1", "TestAgent");

        assertThat(result.getPhoneNumber()).isEqualTo("+998901234567");
        assertThat(result.getMessage()).contains("OTP sent");
        verify(customerRepository).save(any(Customer.class));
        verify(smsService).sendSms(any());
    }

    @Test @DisplayName("requestOtp — existing customer sends new OTP")
    void requestOtp_existingCustomer_sendsOtp() {
        ConsumerLoginRequest request = new ConsumerLoginRequest();
        request.setPhoneNumber("+998901234567");
        request.setRegistrationSource(RegistrationSource.MOBILE_APP);

        when(otpCodeRepository.countRecentOtpsByPhoneNumber(anyString(), any())).thenReturn(0L);
        when(customerRepository.findByPhone("+998901234567")).thenReturn(Optional.of(customer));
        when(otpCodeRepository.save(any(OtpCode.class))).thenAnswer(i -> i.getArgument(0));

        ConsumerLoginResponse result = consumerAuthService.requestOtp(request, "127.0.0.1", "TestAgent");

        assertThat(result.getPhoneNumber()).isEqualTo("+998901234567");
        verify(customerRepository, never()).save(any()); // Not created, just found
    }

    @Test @DisplayName("requestOtp — rate limited throws")
    void requestOtp_rateLimited_throws() {
        ConsumerLoginRequest request = new ConsumerLoginRequest();
        request.setPhoneNumber("+998901234567");
        request.setRegistrationSource(RegistrationSource.MOBILE_APP);

        when(otpCodeRepository.countRecentOtpsByPhoneNumber(anyString(), any())).thenReturn(3L);

        assertThatThrownBy(() -> consumerAuthService.requestOtp(request, "127.0.0.1", "TestAgent"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Too many OTP requests");
    }

    @Test @DisplayName("verifyOtp — success creates session and returns tokens")
    void verifyOtp_success() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setPhoneNumber("+998901234567");
        request.setOtpCode("123456");

        OtpCode otp = OtpCode.builder()
                .id(1L).phoneNumber("+998901234567").otpCode("123456")
                .expiresAt(LocalDateTime.now().plusMinutes(5)).isVerified(false).attempts(0).build();

        when(otpCodeRepository.findByPhoneNumberAndOtpCodeAndIsVerifiedFalse("+998901234567", "123456"))
                .thenReturn(Optional.of(otp));
        when(otpCodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(customerRepository.findByPhone("+998901234567")).thenReturn(Optional.of(customer));
        when(sessionRepository.save(any(ConsumerSession.class))).thenAnswer(i -> i.getArgument(0));

        ConsumerAuthResponse result = consumerAuthService.verifyOtp(request, "127.0.0.1", "TestAgent");

        assertThat(result.getAccessToken()).isNotNull();
        assertThat(result.getRefreshToken()).isNotNull();
        assertThat(result.getCustomerId()).isEqualTo(1L);
        verify(sessionRepository).invalidateAllSessionsByPhoneNumber("+998901234567");
    }

    @Test @DisplayName("verifyOtp — invalid code throws")
    void verifyOtp_invalidCode_throws() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setPhoneNumber("+998901234567");
        request.setOtpCode("000000");

        when(otpCodeRepository.findByPhoneNumberAndOtpCodeAndIsVerifiedFalse("+998901234567", "000000"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> consumerAuthService.verifyOtp(request, "127.0.0.1", "TestAgent"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid OTP");
    }

    @Test @DisplayName("verifyOtp — expired OTP throws")
    void verifyOtp_expired_throws() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setPhoneNumber("+998901234567");
        request.setOtpCode("123456");

        OtpCode expired = OtpCode.builder()
                .id(1L).phoneNumber("+998901234567").otpCode("123456")
                .expiresAt(LocalDateTime.now().minusMinutes(1)).isVerified(false).attempts(0).build();

        when(otpCodeRepository.findByPhoneNumberAndOtpCodeAndIsVerifiedFalse("+998901234567", "123456"))
                .thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> consumerAuthService.verifyOtp(request, "127.0.0.1", "TestAgent"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("expired");
    }

    @Test @DisplayName("refreshAccessToken — success")
    void refreshAccessToken_success() {
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("valid-refresh-token");

        ConsumerSession session = ConsumerSession.builder()
                .id(1L).phoneNumber("+998901234567").customer(customer)
                .sessionToken("old-access").refreshToken("valid-refresh-token")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .refreshExpiresAt(LocalDateTime.now().plusDays(30))
                .isActive(true).build();

        when(sessionRepository.findByRefreshTokenAndIsActiveTrue("valid-refresh-token"))
                .thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ConsumerAuthResponse result = consumerAuthService.refreshAccessToken(request, "127.0.0.1", "TestAgent");

        assertThat(result.getAccessToken()).isNotNull();
        assertThat(result.getPhoneNumber()).isEqualTo("+998901234567");
    }

    @Test @DisplayName("logout — invalidates session")
    void logout_success() {
        ConsumerSession session = ConsumerSession.builder()
                .id(1L).phoneNumber("+998901234567").sessionToken("access-token")
                .isActive(true).build();

        when(sessionRepository.findBySessionTokenAndIsActiveTrue("access-token"))
                .thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        consumerAuthService.logout("access-token");

        assertThat(session.getIsActive()).isFalse();
        verify(sessionRepository).save(session);
    }

    @Test @DisplayName("cleanupExpiredData — deletes expired OTPs and sessions")
    void cleanupExpiredData_deletesOld() {
        consumerAuthService.cleanupExpiredData();

        verify(otpCodeRepository).deleteExpiredOtps(any(LocalDateTime.class));
        verify(sessionRepository).deleteExpiredSessions(any(LocalDateTime.class));
    }
}
