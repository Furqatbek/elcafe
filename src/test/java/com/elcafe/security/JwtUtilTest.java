package com.elcafe.security;

import com.elcafe.modules.auth.enums.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

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

    @Test
    @DisplayName("sign+verify works even with a SHORT secret (regression: WeakKeyException)")
    void shortSecret_stillSignsAndVerifies() {
        // 20 bytes = 160 bits — far below HS384/HS512's raw requirement. Before
        // the SHA-512 derivation this produced a key jjwt rejected on verify.
        JwtUtil shortSecretUtil = new JwtUtil();
        ReflectionTestUtils.setField(shortSecretUtil, "secret", "short-weak-secret-20");
        ReflectionTestUtils.setField(shortSecretUtil, "accessTokenExpiration", 3600000L);
        ReflectionTestUtils.setField(shortSecretUtil, "refreshTokenExpiration", 86400000L);
        ReflectionTestUtils.setField(shortSecretUtil, "waiterTokenExpiration", 2592000000L);

        UserPrincipal principal = new UserPrincipal(1L, "admin@test.com", "pass", UserRole.ADMIN, true, 1L);
        String token = shortSecretUtil.generateAccessToken(principal);

        assertThat(shortSecretUtil.extractUsername(token)).isEqualTo("admin@test.com");
    }

    @Test
    @DisplayName("generated tokens pin the HS512 algorithm in the header")
    void tokenHeaderPinsHs512() {
        UserPrincipal principal = new UserPrincipal(1L, "admin@test.com", "pass", UserRole.ADMIN, true, 1L);
        String token = jwtUtil.generateAccessToken(principal);

        String headerJson = new String(
                Base64.getUrlDecoder().decode(token.split("\\.")[0]), StandardCharsets.UTF_8);
        assertThat(headerJson).contains("\"alg\":\"HS512\"");
    }

    @Test
    @DisplayName("JwtUtil verifies a consumer token signed the ConsumerAuthService way (shared key)")
    void verifiesConsumerTokenSignedWithSharedKey() {
        // Reproduces ConsumerAuthService.generateAccessToken: HS256 signed with
        // the JwtKeys-derived key. The auth filter parses consumer tokens through
        // JwtUtil, so this MUST verify or customer login breaks.
        String secret = "test-secret-key-that-is-at-least-32-characters-long-for-hs256";
        SecretKey consumerKey = JwtKeys.deriveSigningKey(secret);
        String consumerToken = Jwts.builder()
                .setSubject("+998901234567")
                .claim("customerId", 5L)
                .claim("type", "consumer")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3600000L))
                .signWith(consumerKey, SignatureAlgorithm.HS256)
                .compact();

        // jwtUtil is configured with the same secret in setUp().
        Claims claims = jwtUtil.extractAllClaims(consumerToken);
        assertThat(claims.getSubject()).isEqualTo("+998901234567");
        assertThat(claims.get("type", String.class)).isEqualTo("consumer");
        assertThat(claims.get("customerId", Long.class)).isEqualTo(5L);
    }
}
