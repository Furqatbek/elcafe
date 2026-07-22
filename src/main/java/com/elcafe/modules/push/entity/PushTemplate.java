package com.elcafe.modules.push.entity;

import com.elcafe.modules.push.enums.PushNotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Reusable push notification templates.
 */
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "push_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "icon", length = 500)
    private String icon;

    @Column(name = "image", length = 500)
    private String image;

    @Column(name = "badge", length = 500)
    private String badge;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private PushNotificationType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "actions", columnDefinition = "jsonb")
    private Map<String, Object> actions;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_data", columnDefinition = "jsonb")
    private Map<String, Object> defaultData;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "usage_count")
    @Builder.Default
    private Integer usageCount = 0;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Increment usage counter
     */
    public void incrementUsage() {
        this.usageCount++;
    }

    /**
     * Deactivate template
     */
    public void deactivate() {
        this.isActive = false;
    }
}
