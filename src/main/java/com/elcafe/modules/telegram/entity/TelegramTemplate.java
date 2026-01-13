package com.elcafe.modules.telegram.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "telegram_templates")
@EntityListeners(AuditingEntityListener.class)
public class TelegramTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(length = 500)
    private String description;

    @Column(name = "has_image")
    @Builder.Default
    private Boolean hasImage = false;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "has_buttons")
    @Builder.Default
    private Boolean hasButtons = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "buttons_config", columnDefinition = "jsonb")
    private List<Map<String, String>> buttonsConfig;

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

    public String render(Map<String, String> placeholders) {
        String rendered = this.content;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}",
                    entry.getValue() != null ? entry.getValue() : "");
        }
        return rendered;
    }

    public void incrementUsageCount() {
        this.usageCount = (this.usageCount != null ? this.usageCount : 0) + 1;
    }
}
