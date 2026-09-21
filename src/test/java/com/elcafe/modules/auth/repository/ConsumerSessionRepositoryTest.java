package com.elcafe.modules.auth.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.auth.entity.ConsumerSession;
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
class ConsumerSessionRepositoryTest {

    @Autowired private ConsumerSessionRepository sessionRepository;
    @Autowired private EntityManager em;

    @BeforeEach void setUp() {
        // Active session
        em.persist(ConsumerSession.builder()
                .phoneNumber("+998901111111")
                .sessionToken("active-token").refreshToken("active-refresh")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .refreshExpiresAt(LocalDateTime.now().plusDays(30))
                .isActive(true).build());
        // Expired session
        em.persist(ConsumerSession.builder()
                .phoneNumber("+998902222222")
                .sessionToken("expired-token").refreshToken("expired-refresh")
                .expiresAt(LocalDateTime.now().minusHours(1))
                .refreshExpiresAt(LocalDateTime.now().minusDays(1))
                .isActive(true).build());
        em.flush(); em.clear();
    }

    @Test @DisplayName("findBySessionTokenAndIsActiveTrue — finds active session")
    void findByToken() {
        assertTrue(sessionRepository.findBySessionTokenAndIsActiveTrue("active-token").isPresent());
        assertFalse(sessionRepository.findBySessionTokenAndIsActiveTrue("nonexistent").isPresent());
    }

    @Test @DisplayName("findByRefreshTokenAndIsActiveTrue — finds by refresh token")
    void findByRefreshToken() {
        assertTrue(sessionRepository.findByRefreshTokenAndIsActiveTrue("active-refresh").isPresent());
        assertFalse(sessionRepository.findByRefreshTokenAndIsActiveTrue("nonexistent").isPresent());
    }
}
