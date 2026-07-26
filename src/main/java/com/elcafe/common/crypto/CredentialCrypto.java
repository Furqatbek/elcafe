package com.elcafe.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * AES-256-GCM encryption for credentials stored at rest (Instagram/Telegram tokens and secrets).
 *
 * <p><b>Inert without a key.</b> The key is read from the {@code ELCAFE_ENCRYPTION_KEY} environment
 * variable (or the {@code elcafe.encryption.key} system property, for tests) as a base64-encoded 16/24/32
 * byte AES key. When neither is set — dev boxes, CI, an operator who has not provisioned a key yet —
 * {@link #encrypt} returns the plaintext unchanged, so the application behaves exactly as it did before
 * encryption was introduced and nothing breaks on deploy. Provision the key and new writes are encrypted.
 *
 * <p><b>Transparent legacy plaintext.</b> Encrypted values carry the {@code enc:v1:} prefix. {@link
 * #decrypt} passes anything without that prefix through untouched, so rows written before the key existed
 * are still readable; they are re-encrypted the next time the row is saved. No bulk data migration.
 *
 * <p>{@link #blindIndex} is a deterministic keyed hash (HMAC-SHA256) for the few columns that must stay
 * unique or be looked up by value once their plaintext is encrypted — equality without revealing the
 * value.
 */
public final class CredentialCrypto {

    /** Marks a value produced by {@link #encrypt}. Bump the version if the scheme ever changes. */
    static final String PREFIX = "enc:v1:";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private static final String KEY_PROPERTY = "elcafe.encryption.key";
    private static final String KEY_ENV = "ELCAFE_ENCRYPTION_KEY";

    private static final SecureRandom RANDOM = new SecureRandom();

    private CredentialCrypto() {}

    /**
     * The configured AES key, or {@code null} when none is set (encryption inert). Resolved fresh each
     * call — the read is cheap and it keeps tests that set/clear the property from fighting a cache.
     */
    private static SecretKeySpec key() {
        String b64 = System.getProperty(KEY_PROPERTY);
        if (b64 == null || b64.isBlank()) {
            b64 = System.getenv(KEY_ENV);
        }
        if (b64 == null || b64.isBlank()) {
            return null;
        }
        byte[] raw = Base64.getDecoder().decode(b64.trim());
        if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
            throw new IllegalStateException(
                    KEY_ENV + " must be a base64-encoded 128/192/256-bit AES key (got " + raw.length + " bytes)");
        }
        return new SecretKeySpec(raw, "AES");
    }

    /** True when a key is configured — used to skip blind-index/lookup work that needs one. */
    public static boolean isEnabled() {
        return key() != null;
    }

    /**
     * Encrypt {@code plaintext}, or return it unchanged when no key is configured or it is null/empty.
     * A fresh random IV per call means the same plaintext encrypts differently every time.
     */
    public static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }
        SecretKeySpec key = key();
        if (key == null) {
            return plaintext;   // inert: no key provisioned
        }
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt credential", e);
        }
    }

    /**
     * Decrypt a value produced by {@link #encrypt}. A value without the {@code enc:v1:} prefix is legacy
     * plaintext and returned as-is. Throws if an encrypted value is found but no key is configured — that
     * is data loss waiting to happen, not something to paper over with a silent null.
     */
    public static String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            return stored;   // legacy plaintext, written before a key existed
        }
        SecretKeySpec key = key();
        if (key == null) {
            throw new IllegalStateException(
                    "Found an encrypted credential but " + KEY_ENV + " is not set — cannot decrypt");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(combined, GCM_IV_BYTES, combined.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt credential", e);
        }
    }

    /**
     * A deterministic keyed hash of {@code value} for columns that must remain unique or be looked up by
     * value after the value itself is encrypted (Telegram bot_token uniqueness, Instagram verify_token
     * lookup). HMAC-SHA256 under the same key, hex-encoded. Returns {@code null} when {@code value} is
     * null or no key is configured (the caller then falls back to the plaintext column).
     */
    public static String blindIndex(String value) {
        if (value == null) {
            return null;
        }
        SecretKeySpec key = key();
        if (key == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getEncoded(), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute blind index", e);
        }
    }
}
