package com.elcafe.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

import static org.assertj.core.api.Assertions.assertThat;

class JwtKeysTest {

    @Test
    @DisplayName("derives a 512-bit key regardless of secret length")
    void derivesFixed512BitKey() {
        SecretKey shortKey = JwtKeys.deriveSigningKey("tiny");
        SecretKey longKey = JwtKeys.deriveSigningKey(
                "a-very-long-secret-that-exceeds-sixty-four-bytes-for-good-measure-1234567890");

        // SHA-512 output is always 64 bytes = 512 bits.
        assertThat(shortKey.getEncoded()).hasSize(64);
        assertThat(longKey.getEncoded()).hasSize(64);
    }

    @Test
    @DisplayName("derivation is deterministic — same secret yields the same key")
    void deterministic() {
        SecretKey a = JwtKeys.deriveSigningKey("the-same-secret");
        SecretKey b = JwtKeys.deriveSigningKey("the-same-secret");
        assertThat(a.getEncoded()).isEqualTo(b.getEncoded());
    }

    @Test
    @DisplayName("different secrets yield different keys")
    void distinctSecrets() {
        SecretKey a = JwtKeys.deriveSigningKey("secret-one");
        SecretKey b = JwtKeys.deriveSigningKey("secret-two");
        assertThat(a.getEncoded()).isNotEqualTo(b.getEncoded());
    }
}
