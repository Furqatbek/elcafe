package com.elcafe.security;

import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Single source of truth for the HMAC key used to sign and verify all
 * application JWTs (staff access/refresh, waiter, and consumer tokens).
 *
 * <p>Every configured secret is SHA-512-hashed to a fixed 512-bit key. This
 * guarantees the key is always strong enough for HS512 regardless of how long
 * the deployment's {@code app.security.jwt.secret} happens to be — a short
 * secret previously produced a key too weak for the algorithm in the token
 * header, so jjwt threw {@code WeakKeyException} and broke every login and
 * token refresh.
 *
 * <p>Because the derivation is deterministic, {@code JwtUtil} (staff/waiter
 * tokens) and {@code ConsumerAuthService} (customer OTP tokens) derive the
 * identical key from the same secret, so the auth filter — which parses every
 * token type through {@code JwtUtil} — verifies all of them.
 */
public final class JwtKeys {

    private JwtKeys() {
    }

    public static SecretKey deriveSigningKey(String secret) {
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-512").digest(raw); // 64 bytes = 512 bits
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 algorithm unavailable in this JVM", e);
        }
    }
}
