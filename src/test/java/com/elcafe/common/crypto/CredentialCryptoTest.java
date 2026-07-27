package com.elcafe.common.crypto;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The credential-encryption contract: round-trips under a key, stays inert without one, reads legacy
 * plaintext transparently, and authenticates ciphertext. These are the properties that let the converter
 * be deployed before the key is provisioned without breaking anything.
 */
class CredentialCryptoTest {

    // A 32-byte (AES-256) key, base64-encoded. Set as a system property so CredentialCrypto picks it up.
    private static final String KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final String PROP = "elcafe.encryption.key";

    @AfterEach
    void clearKey() {
        System.clearProperty(PROP);
    }

    private void withKey() {
        System.setProperty(PROP, KEY);
    }

    @Test
    @DisplayName("with a key, encrypt→decrypt round-trips and the stored form is prefixed ciphertext")
    void roundTripsUnderAKey() {
        withKey();
        String secret = "EAAG super-secret-page-access-token";

        String stored = CredentialCrypto.encrypt(secret);

        assertThat(stored).startsWith("enc:v1:").isNotEqualTo(secret);
        assertThat(CredentialCrypto.decrypt(stored)).isEqualTo(secret);
    }

    @Test
    @DisplayName("the same plaintext encrypts differently each time (random IV), still decrypts to the same value")
    void nonDeterministic() {
        withKey();
        String a = CredentialCrypto.encrypt("token");
        String b = CredentialCrypto.encrypt("token");

        assertThat(a).isNotEqualTo(b);
        assertThat(CredentialCrypto.decrypt(a)).isEqualTo("token");
        assertThat(CredentialCrypto.decrypt(b)).isEqualTo("token");
    }

    @Test
    @DisplayName("without a key, encryption is inert — the value passes through untouched")
    void inertWithoutKey() {
        assertThat(CredentialCrypto.isEnabled()).isFalse();
        assertThat(CredentialCrypto.encrypt("token")).isEqualTo("token");
        // A plaintext value still round-trips through decrypt (no prefix → returned as-is).
        assertThat(CredentialCrypto.decrypt("token")).isEqualTo("token");
    }

    @Test
    @DisplayName("a legacy plaintext value written before the key existed is still readable once a key is set")
    void legacyPlaintextIsTransparent() {
        withKey();
        // No enc:v1: prefix → treated as legacy plaintext, returned verbatim.
        assertThat(CredentialCrypto.decrypt("legacy-plaintext-token")).isEqualTo("legacy-plaintext-token");
    }

    @Test
    @DisplayName("an encrypted value cannot be decrypted once the key is gone — it throws, never silently nulls")
    void decryptWithoutKeyThrows() {
        withKey();
        String stored = CredentialCrypto.encrypt("token");
        System.clearProperty(PROP);

        assertThatThrownBy(() -> CredentialCrypto.decrypt(stored))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("tampered ciphertext fails authentication (GCM) instead of returning garbage")
    void tamperedCiphertextFailsAuth() {
        withKey();
        String stored = CredentialCrypto.encrypt("token");

        // Flip one character of the base64 body, choosing the replacement from the character being
        // replaced. The previous version picked it from the LAST character while replacing the
        // second-to-last, so roughly one run in sixty produced a string identical to the original —
        // decryption then succeeded, correctly, and the test failed. That is a ~1.6% flake in a suite
        // CI has to keep green, and it looked exactly like a real crypto regression.
        int index = stored.length() - 2;
        char original = stored.charAt(index);
        String tampered = stored.substring(0, index)
                + (original == 'A' ? 'B' : 'A')
                + stored.substring(index + 1);

        // The mutation itself is asserted. A tampering test whose tampering silently did nothing would
        // otherwise pass for the wrong reason on the days it did not fail for the wrong reason.
        assertThat(tampered).isNotEqualTo(stored);

        assertThatThrownBy(() -> CredentialCrypto.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("blind index is deterministic under a key and null without one")
    void blindIndexDeterministicUnderKey() {
        assertThat(CredentialCrypto.blindIndex("bot-token")).isNull();   // no key

        withKey();
        String h1 = CredentialCrypto.blindIndex("bot-token");
        String h2 = CredentialCrypto.blindIndex("bot-token");
        assertThat(h1).isNotNull().isEqualTo(h2);                       // deterministic
        assertThat(CredentialCrypto.blindIndex("other-token")).isNotEqualTo(h1);
        assertThat(h1).doesNotContain("bot-token");                    // does not reveal the value
    }

    @Test
    @DisplayName("null / empty pass through both directions")
    void nullAndEmpty() {
        withKey();
        assertThat(CredentialCrypto.encrypt(null)).isNull();
        assertThat(CredentialCrypto.encrypt("")).isEmpty();
        assertThat(CredentialCrypto.decrypt(null)).isNull();
    }
}
