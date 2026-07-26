package com.elcafe.modules.instagram.entity;

import com.elcafe.modules.instagram.enums.InstagramTriggerType;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * One Instagram automation rule belonging to the owning restaurant — birthday or win-back, today the
 * only two {@link InstagramTriggerType} values. Mirrors {@code TelegramAutomationRule} (V64/V164),
 * adapted to Instagram's per-tenant-from-birth model (V172/V178): a rule belongs to exactly one
 * restaurant's own library, like {@link InstagramTemplate}.
 *
 * <p>This entity only holds the rule's configuration; {@code InstagramScheduler} is what actually reads
 * active rules and sends — see its class javadoc for the Instagram-specific 24-hour messaging-window
 * limitation that applies to every send this rule ever triggers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(
    name = "instagram_automation",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_instagram_automation_restaurant_name",
        columnNames = {"restaurant_id", "name"})
)
@EntityListeners(AuditingEntityListener.class)
// V178: Instagram is a per-tenant channel — scoped by the §3.4 restaurantFilter.
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class InstagramAutomationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning tenant. */
    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 50)
    private InstagramTriggerType triggerType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id", nullable = false)
    private InstagramTemplate template;

    /**
     * Reserved for parity with {@code TelegramAutomationRule.delayMinutes}; NOT honoured by
     * {@code InstagramScheduler}, which always sends immediately during its daily sweep — Telegram's own
     * scheduler never reads this field either. {@code InstagramAutomationService} rejects a non-zero
     * value at create/update time (mirroring {@code SmsAutomationService.requireImmediateDelivery})
     * rather than silently storing a value nothing honours, so in practice this is always 0.
     */
    @Column(name = "delay_minutes")
    @Builder.Default
    private Integer delayMinutes = 0;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    // No explicit columnDefinition = "jsonb": see InstagramTemplate.buttonsConfig's comment — a literal,
    // dialect-blind type name is what H2 rejects ("Unknown data type: JSONB"), and Hibernate 6's own
    // dialect-aware JSON mapping resolves correctly either way (native jsonb on Postgres, matching
    // V178's `conditions JSONB`; H2's own native JSON type in tests).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "conditions")
    private Map<String, Object> conditions;

    @Column(name = "sent_count")
    @Builder.Default
    private Integer sentCount = 0;

    @Column(name = "last_triggered_at")
    private OffsetDateTime lastTriggeredAt;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    public void incrementSentCount() {
        this.sentCount = (this.sentCount != null ? this.sentCount : 0) + 1;
        this.lastTriggeredAt = OffsetDateTime.now();
    }

    /**
     * Whether this rule's extra JSON conditions are satisfied by the given context — mirrors
     * {@code TelegramAutomationRule#shouldTrigger} for API/shape parity. {@code InstagramScheduler}
     * does not call this today: WIN_BACK's {@code days_inactive} threshold is instead applied directly
     * as a SQL cutoff before a subscriber is even loaded (cheaper than loading every subscriber and
     * post-filtering in Java — the same choice {@code TelegramScheduler} makes), and BIRTHDAY has no
     * conditions to check beyond the date match the finder query itself already performs. Kept for the
     * same reason it exists on the Telegram side: a future non-batch, event-driven trigger would need
     * exactly this context-matching contract.
     */
    public boolean shouldTrigger(Map<String, Object> context) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }

        if (conditions.containsKey("days_inactive") && context.containsKey("days_inactive")) {
            Object requiredObj = conditions.get("days_inactive");
            Object actualObj = context.get("days_inactive");
            if (requiredObj != null && actualObj != null) {
                int requiredDays = requiredObj instanceof Number ? ((Number) requiredObj).intValue() : 0;
                int actualDays = actualObj instanceof Number ? ((Number) actualObj).intValue() : 0;
                if (actualDays < requiredDays) {
                    return false;
                }
            }
        }

        return true;
    }
}
