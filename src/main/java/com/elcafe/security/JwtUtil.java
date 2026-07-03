package com.elcafe.security;

import com.elcafe.modules.auth.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

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

    /** The secret that used to ship as a committed default in application.yml — now rejected. */
    private static final String LEAKED_DEFAULT_SECRET =
            "f54a0f3634b3fb7083d03dfe8f54d090a18be3517a0560bab3eb7c192c56edd1";

    /**
     * Fail closed at startup if the signing secret is missing, too weak, or the old public default.
     * A leaked HS256 secret lets anyone forge a token for any user/role, which bypasses every
     * tenant/role check in Phase 0 — so the app must never run with a known or trivial value.
     */
    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "app.security.jwt.secret is not set — provide the JWT_SECRET env var (>= 32 bytes). "
                            + "Refusing to start.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must be at least 32 bytes (256 bits) for HS256.");
        }
        if (LEAKED_DEFAULT_SECRET.equals(secret)) {
            throw new IllegalStateException(
                    "app.security.jwt.secret is the old committed default, which is public. "
                            + "Set a fresh JWT_SECRET and rotate.");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
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
        try {
            return extractExpiration(token).before(new Date());
        } catch (ExpiredJwtException e) {
            // Parsing itself rejects an expired token before we can read the date.
            return true;
        }
    }

    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("tokenVersion", tokenVersionOf(userDetails));
        return createToken(claims, userDetails.getUsername(), accessTokenExpiration);
    }

    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "refresh");
        claims.put("tokenVersion", tokenVersionOf(userDetails));
        return createToken(claims, userDetails.getUsername(), refreshTokenExpiration);
    }

    /** Current token version for a principal (0 for token types that don't track it). */
    private int tokenVersionOf(UserDetails userDetails) {
        if (userDetails instanceof UserPrincipal principal) {
            return principal.getTokenVersion();
        }
        if (userDetails instanceof User user && user.getTokenVersion() != null) {
            return user.getTokenVersion();
        }
        return 0;
    }

    /**
     * Generate access token for waiter authentication
     * Uses extended expiration time (30 days) for waiters
     */
    public String generateWaiterAccessToken(String identifier, Long waiterId, String role, Long restaurantId,
                                            Integer tokenVersion) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("waiterId", waiterId);
        claims.put("role", role);
        claims.put("type", "waiter");
        // §3.5: revocation version, checked per request by JwtAuthenticationFilter (a missing claim
        // counts as 0). Bumped on PIN change / deactivation to kill outstanding long-lived tokens.
        claims.put("tokenVersion", tokenVersion == null ? 0 : tokenVersion);
        // §3.6: bind the waiter to a tenant so their requests can be tenant-scoped. Omitted when the
        // waiter has no restaurant yet (e.g. a legacy row not backfilled), preserving prior behaviour.
        if (restaurantId != null) {
            claims.put("restaurantId", restaurantId);
        }
        return createToken(claims, identifier, waiterTokenExpiration);
    }

    /**
     * Generate a long-lived token for a print agent (a headless device). Carries a {@code restaurantId}
     * claim so {@link com.elcafe.modules.waiter.websocket.StompAuthChannelInterceptor} binds the STOMP
     * session to that tenant and only lets it read its own restaurant's print topic. The agent presents
     * this on the WebSocket CONNECT. Minted by an ADMIN for their own restaurant; re-mint to rotate.
     */
    public String generatePrintAgentToken(Long restaurantId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "print-agent");
        claims.put("restaurantId", restaurantId);
        long oneYearMs = 365L * 24 * 60 * 60 * 1000;
        return createToken(claims, "print-agent:" + restaurantId, oneYearMs);
    }

    /**
     * Generate refresh token for waiter
     */
    public String generateWaiterRefreshToken(String identifier) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", "waiter_refresh");
        return createToken(claims, identifier, refreshTokenExpiration);
    }

    private String createToken(Map<String, Object> claims, String subject, Long expiration) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        try {
            final Claims claims = extractAllClaims(token); // single parse; throws if expired/invalid
            if (!claims.getSubject().equals(userDetails.getUsername())) {
                return false;
            }
            // Instant revocation (§3.5): a token is dead once its version trails the user's current
            // one (bumped on password change/reset / "log out everywhere"). A missing claim counts
            // as 0, so tokens issued before this existed stay valid until the first bump.
            Integer claimVersion = claims.get("tokenVersion", Integer.class);
            return (claimVersion == null ? 0 : claimVersion) == tokenVersionOf(userDetails);
        } catch (JwtException e) {
            // Expired, malformed, or bad-signature tokens are simply invalid.
            return false;
        }
    }
}
