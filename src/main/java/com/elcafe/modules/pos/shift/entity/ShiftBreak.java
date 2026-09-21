package com.elcafe.modules.pos.shift.entity;

import com.elcafe.modules.pos.shift.enums.BreakType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Tracks break periods within an employee shift.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "shift_breaks", indexes = {
    @Index(name = "idx_shift_breaks_shift", columnList = "shift_id")
})
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class ShiftBreak {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false)
    private EmployeeShift shift;

    @Column(name = "break_start", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime breakStart;

    @Column(name = "break_end", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime breakEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "break_type", length = 50)
    @Builder.Default
    private BreakType breakType = BreakType.BREAK;

    @Column(name = "is_paid")
    @Builder.Default
    private Boolean isPaid = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    public int getDurationMinutes() {
        if (breakEnd == null) {
            return (int) Duration.between(breakStart, OffsetDateTime.now()).toMinutes();
        }
        return (int) Duration.between(breakStart, breakEnd).toMinutes();
    }

    public boolean isActive() {
        return breakEnd == null;
    }
}
