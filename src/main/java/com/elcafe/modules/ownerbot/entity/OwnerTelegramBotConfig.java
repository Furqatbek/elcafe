package com.elcafe.modules.ownerbot.entity;

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
 * Entity representing Owner Telegram bot configuration per restaurant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "owner_telegram_bot_config")
@EntityListeners(AuditingEntityListener.class)
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class OwnerTelegramBotConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "restaurant_id")
    private Long restaurantId;

    /** Telegram bot token — full control of the owner bot. Encrypted at rest (V169); widened to TEXT. */
    @Column(name = "bot_token", columnDefinition = "TEXT")
    @Convert(converter = EncryptedStringConverter.class)
    private String botToken;

    @Column(name = "bot_username", length = 100)
    private String botUsername;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "welcome_message", columnDefinition = "TEXT")
    private String welcomeMessage;

    @Column(name = "auto_verify_owners")
    @Builder.Default
    private Boolean autoVerifyOwners = true;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
