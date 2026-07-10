package com.elcafe.security;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.auth.enums.UserRole;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

    @Test @DisplayName("generateWaiterAccessToken — contains waiterId, role and tokenVersion claims")
    void generateConsumerToken_containsClaims() {
        String token = jwtUtil.generateWaiterAccessToken("waiter-pin", 5L, "WAITER", 42L, 3);

        Claims claims = jwtUtil.extractAllClaims(token);
        assertThat(claims.getSubject()).isEqualTo("waiter-pin");
        assertThat(claims.get("waiterId", Long.class)).isEqualTo(5L);
        assertThat(claims.get("type", String.class)).isEqualTo("waiter");
        assertThat(claims.get("restaurantId", Long.class)).isEqualTo(42L);
        // §3.5: revocation version is embedded for the filter to check per request.
        assertThat(claims.get("tokenVersion", Integer.class)).isEqualTo(3);
    }

    @Test @DisplayName("generateWaiterAccessToken — null tokenVersion defaults to 0")
    void generateWaiterToken_nullVersion_defaultsToZero() {
        String token = jwtUtil.generateWaiterAccessToken("waiter-pin", 5L, "WAITER", 42L, null);
        assertThat(jwtUtil.extractAllClaims(token).get("tokenVersion", Integer.class)).isEqualTo(0);
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

    @Test @DisplayName("validateToken — expired token returns false (not throws)")
    void validateToken_expired_returnsFalse() {
        ReflectionTestUtils.setField(jwtUtil, "accessTokenExpiration", -10_000L); // already past
        UserPrincipal principal = new UserPrincipal(1L, "admin@test.com", "pass", UserRole.ADMIN, true, 1L);
        String token = jwtUtil.generateAccessToken(principal);

        assertThat(jwtUtil.validateToken(token, principal)).isFalse();
    }

    @Test @DisplayName("validateSecret — rejects blank, too-short, and the old committed secret")
    void validateSecret_rejectsWeakSecrets() {
        ReflectionTestUtils.setField(jwtUtil, "secret", "");
        assertThatThrownBy(jwtUtil::validateSecret).isInstanceOf(IllegalStateException.class);

        ReflectionTestUtils.setField(jwtUtil, "secret", "too-short");
        assertThatThrownBy(jwtUtil::validateSecret).isInstanceOf(IllegalStateException.class);

        ReflectionTestUtils.setField(jwtUtil, "secret",
                "f54a0f3634b3fb7083d03dfe8f54d090a18be3517a0560bab3eb7c192c56edd1");
        assertThatThrownBy(jwtUtil::validateSecret).isInstanceOf(IllegalStateException.class);

        // the committed .env.docker placeholder is long enough but public — must also be rejected
        ReflectionTestUtils.setField(jwtUtil, "secret", "your_super_secret_jwt_key_minimum_256_bits_here");
        assertThatThrownBy(jwtUtil::validateSecret).isInstanceOf(IllegalStateException.class);
    }

    @Test @DisplayName("validateSecret — accepts a strong, non-default secret")
    void validateSecret_acceptsStrong() {
        // the @BeforeEach secret is strong and not the committed one
        assertThatCode(jwtUtil::validateSecret).doesNotThrowAnyException();
    }

    @Test @DisplayName("validateToken — token whose version trails the user's is revoked")
    void validateToken_staleVersion_returnsFalse() {
        UserPrincipal principal = new UserPrincipal(1L, "admin@test.com", "pass", UserRole.ADMIN, true, 1L, 0);
        String token = jwtUtil.generateAccessToken(principal); // embeds tokenVersion 0

        UserPrincipal afterRevoke = new UserPrincipal(1L, "admin@test.com", "pass", UserRole.ADMIN, true, 1L, 1);
        assertThat(jwtUtil.validateToken(token, afterRevoke)).isFalse();   // version bumped -> revoked
        assertThat(jwtUtil.validateToken(token, principal)).isTrue();      // same version -> still valid
    }
    @Test
    @DisplayName("generatePrintAgentToken — carries restaurantId + type and is a valid unexpired token")
    void printAgentToken_hasScopedClaims() {
        String token = jwtUtil.generatePrintAgentToken(7L);
        Claims claims = jwtUtil.extractAllClaims(token);
        assertThat(claims.get("type", String.class)).isEqualTo("print-agent");
        assertThat(claims.get("restaurantId", Long.class)).isEqualTo(7L);
        assertThat(jwtUtil.isTokenExpired(token)).isFalse();
    }
}
