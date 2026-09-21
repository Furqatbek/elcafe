package com.elcafe.security;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "test-secret-key-that-is-at-least-32-characters-long-for-hs256");
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpiration", 3600000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshTokenExpiration", 86400000L);
        ReflectionTestUtils.setField(jwtUtil, "waiterTokenExpiration", 2592000000L);
    }

    @Test @DisplayName("generateAccessToken — contains email as subject")
    void generateToken_containsClaims() {
        UserPrincipal principal = new UserPrincipal(1L, "admin@test.com", "pass", UserRole.ADMIN, true, 1L);
        String token = jwtUtil.generateAccessToken(principal);

        assertThat(token).isNotEmpty();
        String username = jwtUtil.extractUsername(token);
        assertThat(username).isEqualTo("admin@test.com");
    }

    @Test @DisplayName("generateWaiterAccessToken — contains waiterId and role claims")
    void generateConsumerToken_containsClaims() {
        String token = jwtUtil.generateWaiterAccessToken("waiter-pin", 5L, "WAITER");

        Claims claims = jwtUtil.extractAllClaims(token);
        assertThat(claims.getSubject()).isEqualTo("waiter-pin");
        assertThat(claims.get("waiterId", Long.class)).isEqualTo(5L);
        assertThat(claims.get("type", String.class)).isEqualTo("waiter");
    }

    @Test @DisplayName("validateToken — valid token returns true")
    void validateToken_validToken_returnsTrue() {
        UserPrincipal principal = new UserPrincipal(1L, "admin@test.com", "pass", UserRole.ADMIN, true, 1L);
        String token = jwtUtil.generateAccessToken(principal);

        assertThat(jwtUtil.validateToken(token, principal)).isTrue();
    }

    @Test @DisplayName("validateToken — wrong user returns false")
    void validateToken_wrongUser_returnsFalse() {
        UserPrincipal principal = new UserPrincipal(1L, "admin@test.com", "pass", UserRole.ADMIN, true, 1L);
        UserPrincipal other = new UserPrincipal(2L, "other@test.com", "pass", UserRole.OPERATOR, true, 1L);
        String token = jwtUtil.generateAccessToken(principal);

        assertThat(jwtUtil.validateToken(token, other)).isFalse();
    }

    @Test @DisplayName("validateToken — tampered token throws")
    void validateToken_tamperedToken_throws() {
        UserPrincipal principal = new UserPrincipal(1L, "admin@test.com", "pass", UserRole.ADMIN, true, 1L);
        String token = jwtUtil.generateAccessToken(principal);
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThatThrownBy(() -> jwtUtil.extractUsername(tampered))
                .isInstanceOf(Exception.class);
    }

    @Test @DisplayName("extractUsername — returns subject from token")
    void extractUsername_returnsSubject() {
        UserPrincipal principal = new UserPrincipal(1L, "cashier@test.com", "pass", UserRole.OPERATOR, true, 1L);
        String token = jwtUtil.generateAccessToken(principal);

        assertThat(jwtUtil.extractUsername(token)).isEqualTo("cashier@test.com");
    }
}
