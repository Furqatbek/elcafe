package com.elcafe.modules.telegram.entity;

import com.elcafe.common.crypto.CredentialCrypto;
import com.elcafe.common.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * Entity representing Telegram bot configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "telegram_bot_config",
    // Blind-index uniqueness: forbids two restaurants registering the same bot once the token is
    // encrypted. NULLs are distinct in SQL, so legacy rows (null hash until re-saved) coexist.
    uniqueConstraints = @UniqueConstraint(name = "uq_tg_config_bot_token_hash", columnNames = "bot_token_hash"))
@EntityListeners(AuditingEntityListener.class)
// V164: Telegram is a per-tenant channel — scoped by the §3.4 restaurantFilter.
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class TelegramBotConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning tenant. Each restaurant runs its own bot with its own token. */
    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    /**
     * Telegram bot token — full control of the bot, and the tenant key for every inbound update.
     * Encrypted at rest (V170); widened to TEXT. Because the value is now non-deterministic ciphertext,
     * the "one bot per token" guarantee moves to {@link #botTokenHash} (a deterministic blind index).
     */
    @Column(name = "bot_token", nullable = false, columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String botToken;

    /**
     * Deterministic keyed hash of {@link #botToken}, maintained by {@link #syncBotTokenHash()} so a
     * unique index can still forbid two restaurants registering the same bot once the token is encrypted.
     * Null when no encryption key is configured (uniqueness is then enforced on the plaintext column).
     */
    @Column(name = "bot_token_hash", length = 64)
    private String botTokenHash;

    @Column(name = "bot_username", nullable = false, length = 100)
    private String botUsername;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "welcome_message", columnDefinition = "TEXT")
    private String welcomeMessage;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Keep the blind index in step with the token. Runs BEFORE the {@code @Convert} encrypts the column,
     * so it hashes the plaintext token. No-op (null hash) when no key is configured.
     */
    @PrePersist
    @PreUpdate
    private void syncBotTokenHash() {
        this.botTokenHash = CredentialCrypto.blindIndex(this.botToken);
    }
}
