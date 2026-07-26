package com.elcafe.modules.instagram.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * One reusable Instagram DM message template belonging to the owning restaurant: body text with
 * {@code {placeholder}} substitution, an optional image, and optional button/quick-reply
 * configuration. Mirrors {@code TelegramTemplate} (V64), adapted to Instagram's per-tenant model
 * (V163/V166): a template belongs to exactly one restaurant's own library rather than a global,
 * platform-wide catalog.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(
    name = "instagram_templates",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_instagram_templates_restaurant_name",
        columnNames = {"restaurant_id", "name"})
)
@EntityListeners(AuditingEntityListener.class)
// V172: Instagram is a per-tenant channel — scoped by the §3.4 restaurantFilter.
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class InstagramTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning tenant. */
    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "message_text", nullable = false, columnDefinition = "TEXT")
    private String messageText;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "has_image", nullable = false)
    @Builder.Default
    private Boolean hasImage = false;

    @Column(name = "has_buttons", nullable = false)
    @Builder.Default
    private Boolean hasButtons = false;

    // No explicit columnDefinition: TelegramTemplate hardcodes columnDefinition = "jsonb", which is
    // fine on Postgres but is a literal, dialect-blind type name that H2 rejects ("Unknown data type:
    // JSONB") — the reason no Telegram/Push template entity has an H2-backed repository test today.
    // Leaving the type to Hibernate 6's own dialect-aware JSON mapping resolves correctly either way:
    // PostgreSQLDialect maps SqlTypes.JSON to a native jsonb column (matching V172's buttons_config
    // JSONB), while H2Dialect maps it to H2's own native JSON type.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "buttons_config")
    private List<Map<String, String>> buttonsConfig;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private Integer usageCount = 0;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /** Placeholder substitution: replaces every {@code {key}} in the message text with its value. */
    public String render(Map<String, String> placeholders) {
        String rendered = this.messageText;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                rendered = rendered.replace("{" + entry.getKey() + "}",
                        entry.getValue() != null ? entry.getValue() : "");
            }
        }
        return rendered;
    }

    public void incrementUsageCount() {
        this.usageCount = (this.usageCount != null ? this.usageCount : 0) + 1;
    }
}
