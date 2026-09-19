package com.elcafe.modules.partner.entity;

import com.elcafe.modules.partner.enums.IntegrationEventStatus;
import com.elcafe.modules.partner.enums.IntegrationEventType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.OffsetDateTime;

/**
 * One message queued for delivery to an integration partner.
 *
 * <p>Written in the same transaction as the change that caused it, so the message cannot exist for a
 * change that rolled back, nor go missing for one that committed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "integration_events")
@EntityListeners(AuditingEntityListener.class)
public class IntegrationEvent {

    /** Backoff ceiling. A partner's outage can outlast any sensible doubling, so it stops at 30 min. */
    private static final long MAX_BACKOFF_SECONDS = 1800;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "partner_id", nullable = false)
    private Long partnerId;

    @Column(name = "restaurant_id", nullable = false)
    private Long restaurantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private IntegrationEventType eventType;

    /** What this is about ("order:512"). Drives coalescing and per-subject ordering. */
    @Column(name = "subject_key", nullable = false, length = 120)
    private String subjectKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private IntegrationEventStatus status = IntegrationEventStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private Integer maxAttempts = 10;

    @Column(name = "next_attempt_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    @Builder.Default
    private OffsetDateTime nextAttemptAt = OffsetDateTime.now();

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "dispatched_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime dispatchedAt;

    @Column(name = "dead_lettered_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime deadLetteredAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    /** The partner accepted it. */
    public void markSent() {
        this.status = IntegrationEventStatus.SENT;
        this.dispatchedAt = OffsetDateTime.now();
        this.lastError = null;
    }

    /**
     * Records a failed attempt and schedules the next one, or dead-letters once the attempts are spent.
     *
     * <p>Backoff doubles from 2s and stops at 30 minutes: a partner deploying takes seconds, a partner
     * having an incident takes hours, and hammering them through the second case helps nobody.
     */
    public void markAttemptFailed(String error) {
        this.attemptCount = (this.attemptCount == null ? 0 : this.attemptCount) + 1;
        this.lastError = truncate(error);

        if (this.attemptCount >= this.maxAttempts) {
            this.status = IntegrationEventStatus.DEAD_LETTER;
            this.deadLetteredAt = OffsetDateTime.now();
            return;
        }

        long backoff = Math.min((long) Math.pow(2, Math.min(this.attemptCount, 11)), MAX_BACKOFF_SECONDS);
        this.nextAttemptAt = OffsetDateTime.now().plusSeconds(backoff);
    }

    /** A newer event for this subject arrived first. */
    public void markSuperseded() {
        this.status = IntegrationEventStatus.SUPERSEDED;
    }

    /** Put a dead-lettered event back in the queue — the operator's "the partner is back" button. */
    public void requeue() {
        this.status = IntegrationEventStatus.PENDING;
        this.attemptCount = 0;
        this.deadLetteredAt = null;
        this.nextAttemptAt = OffsetDateTime.now();
    }

    /** The column is 1000 chars and a partner's error body can be an entire HTML page. */
    private String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() > 1000 ? error.substring(0, 1000) : error;
    }
}
