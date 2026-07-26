package com.elcafe.modules.instagram.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.instagram.entity.InstagramBotConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the Instagram credential columns are encrypted at rest (V169): with a key configured, the raw
 * {@code app_secret} / {@code access_token} columns hold {@code enc:v1:} ciphertext — a DB dump yields no
 * usable token — while the entity still exposes the plaintext. Also proves a row written before the key
 * existed stays readable (transparent legacy plaintext), which is what makes the rollout non-breaking.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class InstagramBotConfigEncryptionTest {

    private static final String KEY_PROP = "elcafe.encryption.key";
    private static final String KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Autowired private InstagramBotConfigRepository repo;
    @Autowired private EntityManager em;

    @BeforeEach
    void setKey() {
        System.setProperty(KEY_PROP, KEY);
    }

    @AfterEach
    void clearKey() {
        System.clearProperty(KEY_PROP);
    }

    @Test
    @DisplayName("tokens are ciphertext in the column but plaintext through the entity")
    void tokensEncryptedAtRest() {
        InstagramBotConfig cfg = InstagramBotConfig.builder()
                .restaurantId(1L)
                .appSecret("app-secret-value")
                .accessToken("access-token-value")
                .isActive(true)
                .build();
        Long id = repo.saveAndFlush(cfg).getId();
        em.clear();

        Object[] raw = (Object[]) em.createNativeQuery(
                "SELECT app_secret, access_token FROM instagram_bot_config WHERE id = :id")
                .setParameter("id", id)
                .getSingleResult();
        assertThat((String) raw[0]).startsWith("enc:v1:").doesNotContain("app-secret-value");
        assertThat((String) raw[1]).startsWith("enc:v1:").doesNotContain("access-token-value");

        InstagramBotConfig reloaded = repo.findById(id).orElseThrow();
        assertThat(reloaded.getAppSecret()).isEqualTo("app-secret-value");
        assertThat(reloaded.getAccessToken()).isEqualTo("access-token-value");
    }

    @Test
    @DisplayName("a row written before the key existed is still readable once a key is set")
    void legacyPlaintextStaysReadable() {
        System.clearProperty(KEY_PROP);   // no key yet → stored as plaintext
        InstagramBotConfig legacy = InstagramBotConfig.builder()
                .restaurantId(2L)
                .appSecret("legacy-secret")
                .accessToken("legacy-token")
                .isActive(true)
                .build();
        Long id = repo.saveAndFlush(legacy).getId();
        em.clear();

        // Verify it really was stored in the clear (no enc: prefix).
        String rawSecret = (String) em.createNativeQuery(
                "SELECT app_secret FROM instagram_bot_config WHERE id = :id")
                .setParameter("id", id)
                .getSingleResult();
        assertThat(rawSecret).isEqualTo("legacy-secret");

        // A key now appears; the legacy plaintext row must not become unreadable.
        System.setProperty(KEY_PROP, KEY);
        InstagramBotConfig reloaded = repo.findById(id).orElseThrow();
        assertThat(reloaded.getAppSecret()).isEqualTo("legacy-secret");
        assertThat(reloaded.getAccessToken()).isEqualTo("legacy-token");
    }
}
