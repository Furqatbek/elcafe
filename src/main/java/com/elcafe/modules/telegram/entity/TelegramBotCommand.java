package com.elcafe.modules.telegram.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * Entity representing Telegram bot commands.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(
    name = "telegram_bot_commands",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_tg_command_restaurant",
        columnNames = {"restaurant_id", "command"})
)
@EntityListeners(AuditingEntityListener.class)
// V164: Telegram is a per-tenant channel — scoped by the §3.4 restaurantFilter.
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class TelegramBotCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning tenant — commands are per-bot, so "/start" exists once per restaurant. */
    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    // Per-tenant uniqueness (uq_tg_command_restaurant, V164).
    @Column(nullable = false, length = 50)
    private String command;

    @Column(length = 200)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "response_template_id")
    private TelegramTemplate responseTemplate;

    @Column(name = "custom_response", columnDefinition = "TEXT")
    private String customResponse;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "usage_count")
    @Builder.Default
    private Integer usageCount = 0;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Increment usage count when command is executed.
     */
    public void recordUsage() {
        this.usageCount = (this.usageCount == null ? 0 : this.usageCount) + 1;
    }
}
