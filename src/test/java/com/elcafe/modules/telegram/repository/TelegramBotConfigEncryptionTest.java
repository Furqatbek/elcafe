package com.elcafe.modules.telegram.repository;

import com.elcafe.config.JpaConfig;
import com.elcafe.modules.telegram.entity.TelegramBotConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the Telegram bot token is encrypted at rest (V170) AND that its "one bot per token" guarantee
 * survives the switch to non-deterministic ciphertext via the {@code bot_token_hash} blind index: the raw
 * column holds {@code enc:v1:} ciphertext, the hash is a deterministic HMAC of the plaintext, the entity
 * round-trips, and two restaurants cannot register the same bot.
 */
@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class TelegramBotConfigEncryptionTest {

    private static final String KEY_PROP = "elcafe.encryption.key";
    private static final String KEY =
            Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Autowired private TelegramBotConfigRepository repo;
    @Autowired private EntityManager em;

    @BeforeEach
    void setKey() {
        System.setProperty(KEY_PROP, KEY);
    }

    @AfterEach
    void clearKey() {
        System.clearProperty(KEY_PROP);
    }

    private TelegramBotConfig config(Long restaurantId, String token, String username) {
        return TelegramBotConfig.builder()
                .restaurantId(restaurantId).botToken(token).botUsername(username).isActive(true).build();
    }

    @Test
    @DisplayName("bot_token is ciphertext at rest with a populated blind index; the entity round-trips")
    void tokenEncryptedWithBlindIndex() {
        Long id = repo.saveAndFlush(config(1L, "123456:secret-bot-token", "shop_bot")).getId();
        em.clear();

        Object[] raw = (Object[]) em.createNativeQuery(
                "SELECT bot_token, bot_token_hash FROM telegram_bot_config WHERE id = :id")
                .setParameter("id", id)
                .getSingleResult();
        assertThat((String) raw[0]).startsWith("enc:v1:").doesNotContain("secret-bot-token");
        assertThat((String) raw[1]).isNotNull().hasSize(64);   // hex HMAC-SHA256, not the token

        assertThat(repo.findById(id).orElseThrow().getBotToken()).isEqualTo("123456:secret-bot-token");
    }

    @Test
    @DisplayName("two restaurants cannot register the same bot token — the blind index rejects the duplicate")
    void sameTokenAcrossRestaurantsRejected() {
        repo.saveAndFlush(config(1L, "same-token", "bot_a"));

        // Different restaurant, different username, SAME token: the ciphertext differs (random IV) so the
        // plaintext index can't catch it, but the deterministic hash index must.
        assertThatThrownBy(() -> repo.saveAndFlush(config(2L, "same-token", "bot_b")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
