package com.elcafe.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Component
public class JwtUtil {

    @Value("${app.security.jwt.secret}")
    private String secret;

    @Value("${app.security.jwt.access-token-expiration}")
    private Long accessTokenExpiration;

    @Value("${app.security.jwt.refresh-token-expiration}")
    private Long refreshTokenExpiration;

    @Value("${app.security.jwt.waiter-token-expiration}")
    private Long waiterTokenExpiration;

    @Value("${app.security.jwt.waiter-refresh-token-expiration}")
    private Long waiterRefreshTokenExpiration;

    // Derived once, then cached. Lazy so both Spring (field-injected secret)
    // and plain unit tests (ReflectionTestUtils) build the key correctly
    // without needing a lifecycle callback.
    private volatile SecretKey signingKey;

    /**
     * The HMAC signing key, derived once from the configured secret via
     * {@link JwtKeys#deriveSigningKey(String)} and cached. Lazy so both Spring
     * (field-injected secret) and plain unit tests (ReflectionTestUtils) build
     * the key without needing a lifecycle callback.
     */
    private SecretKey getSigningKey() {
        SecretKey key = signingKey;
        if (key == null) {
            synchronized (this) {
                key = signingKey;
                if (key == null) {
                    int rawLen = secret.getBytes(StandardCharsets.UTF_8).length;
                    if (rawLen < 32) {
                        log.warn("Configured JWT secret is only {} bytes. It is hashed to a 512-bit "
                                + "signing key so tokens still work, but set a strong app.security.jwt.secret "
                                + "(JWT_SECRET env, >= 64 random chars) for real entropy.", rawLen);
                    }
                    key = JwtKeys.deriveSigningKey(secret);
                    signingKey = key;
                }
            }
        }
        return key;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Boolean isTokenExpired(String token) {
        // Tokens are now stateless with no expiration
        return false;
    }

    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userDetails.getUsername(), accessTokenExpiration);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        return createToken(claims, userDetails.getUsername(), refreshTokenExpiration);
    }

    /**
     * Generate access token for waiter authentication
     * Uses extended expiration time (30 days) for waiters
     */
    public String generateWaiterAccessToken(String identifier, Long waiterId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("waiterId", waiterId);
        claims.put("role", role);
        claims.put("type", "waiter");
        return createToken(claims, identifier, waiterTokenExpiration);
    }

    /**
     * Generate refresh token for waiter. Carries the waiterId so the refresh
     * endpoint can reload the waiter (to re-check active status and re-issue
     * an access token) without a separate lookup keyed on the subject.
     */
    public String generateWaiterRefreshToken(String identifier, Long waiterId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "waiter_refresh");
        claims.put("waiterId", waiterId);
        return createToken(claims, identifier, waiterRefreshTokenExpiration);
    }

    private String createToken(Map<String, Object> claims, String subject, Long expiration) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                // Pin HS512 explicitly rather than letting jjwt infer the
                // algorithm from key length — the derived key is always 512 bits.
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
}
