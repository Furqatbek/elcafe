package com.elcafe.modules.auth.integration;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.auth.entity.OtpCode;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import com.elcafe.modules.auth.repository.OtpCodeRepository;
import com.elcafe.modules.auth.repository.UserRepository;
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

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class AuthIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private OtpCodeRepository otpCodeRepository;
    @Autowired private EntityManager em;

    @BeforeEach
    void setUp() {
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("User persist and find by email")
    void registerAndLogin_fullFlow() {
        User user = User.builder()
                .email("test@example.com").password("$2a$10$encoded")
                .firstName("Test").lastName("User")
                .role(UserRole.ADMIN).active(true).build();
        user = userRepository.save(user);
        em.flush();
        em.clear();

        // Find by email (login lookup)
        assertTrue(userRepository.findByEmail("test@example.com").isPresent());
        assertEquals(user.getId(), userRepository.findByEmail("test@example.com").get().getId());
    }

    @Test
    @DisplayName("Operator CRUD: create → read → update → delete")
    void operatorCRUD_fullFlow() {
        // Create
        User op = userRepository.save(User.builder()
                .email("op@test.com").password("$2a$encoded")
                .firstName("Op").lastName("User")
                .role(UserRole.OPERATOR).active(true).build());
        em.flush();
        em.clear();

        // Read
        User loaded = userRepository.findById(op.getId()).orElseThrow();
        assertEquals("op@test.com", loaded.getEmail());
        assertEquals(UserRole.OPERATOR, loaded.getRole());

        // Update
        loaded.setFirstName("Updated");
        userRepository.save(loaded);
        em.flush();
        em.clear();

        assertEquals("Updated", userRepository.findById(op.getId()).orElseThrow().getFirstName());

        // Delete
        userRepository.deleteById(op.getId());
        em.flush();
        em.clear();

        assertFalse(userRepository.findById(op.getId()).isPresent());
    }

    @Test
    @DisplayName("OTP code persist and lookup")
    void consumerOtp_fullFlow() {
        OtpCode otp = OtpCode.builder()
                .phoneNumber("+998901234567").otpCode("123456")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .isVerified(false).attempts(0).build();
        otpCodeRepository.save(otp);
        em.flush();
        em.clear();

        // Find by phone + code + unverified
        assertTrue(otpCodeRepository.findByPhoneNumberAndOtpCodeAndIsVerifiedFalse(
                "+998901234567", "123456").isPresent());

        // Find latest valid
        assertTrue(otpCodeRepository.findLatestValidOtp(
                "+998901234567", LocalDateTime.now()).isPresent());
    }

    @Test
    @DisplayName("Unique email constraint at DB level")
    void userUniqueEmail_constraint() {
        userRepository.save(User.builder()
                .email("unique@test.com").password("pass")
                .firstName("A").lastName("B")
                .role(UserRole.OPERATOR).active(true).build());
        em.flush();

        assertThrows(Exception.class, () -> {
            userRepository.save(User.builder()
                    .email("unique@test.com").password("pass2")
                    .firstName("C").lastName("D")
                    .role(UserRole.OPERATOR).active(true).build());
            em.flush();
        });
    }
}
