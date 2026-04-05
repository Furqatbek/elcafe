package com.elcafe.modules.auth.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.auth.entity.OtpCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest @ActiveProfiles("test") @Import(JpaConfig.class)
class OtpCodeRepositoryTest {

    @Autowired private OtpCodeRepository otpCodeRepository;
    @Autowired private EntityManager em;

    @BeforeEach void setUp() {
        // Valid OTP
        em.persist(OtpCode.builder().phoneNumber("+998901111111").otpCode("111111")
                .expiresAt(LocalDateTime.now().plusMinutes(5)).isVerified(false).attempts(0).build());
        // Expired OTP
        em.persist(OtpCode.builder().phoneNumber("+998902222222").otpCode("222222")
                .expiresAt(LocalDateTime.now().minusMinutes(10)).isVerified(false).attempts(0).build());
        // Verified OTP
        em.persist(OtpCode.builder().phoneNumber("+998903333333").otpCode("333333")
                .expiresAt(LocalDateTime.now().plusMinutes(5)).isVerified(true).attempts(1).build());
        em.flush(); em.clear();
    }

    @Test @DisplayName("findLatestValidOtp — returns unverified, unexpired")
    void findLatestValid() {
        assertTrue(otpCodeRepository.findLatestValidOtp("+998901111111", LocalDateTime.now()).isPresent());
        // Expired — not found
        assertFalse(otpCodeRepository.findLatestValidOtp("+998902222222", LocalDateTime.now()).isPresent());
    }

    @Test @DisplayName("findByPhoneNumberAndOtpCodeAndIsVerifiedFalse — finds unverified match")
    void findByPhoneAndCode() {
        assertTrue(otpCodeRepository.findByPhoneNumberAndOtpCodeAndIsVerifiedFalse("+998901111111", "111111").isPresent());
        // Already verified
        assertFalse(otpCodeRepository.findByPhoneNumberAndOtpCodeAndIsVerifiedFalse("+998903333333", "333333").isPresent());
    }

    @Test @DisplayName("countRecentOtpsByPhoneNumber — counts for rate limiting")
    void countRecent() {
        long count = otpCodeRepository.countRecentOtpsByPhoneNumber("+998901111111", LocalDateTime.now().minusHours(1));
        assertEquals(1, count);
    }
}
