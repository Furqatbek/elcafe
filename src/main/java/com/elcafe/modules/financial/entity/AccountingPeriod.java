package com.elcafe.modules.financial.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity(name = "FinancialAccountingPeriod")
@Table(name = "financial_accounting_periods", indexes = {
        @Index(name = "idx_accounting_period_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_accounting_period_dates", columnList = "start_date, end_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountingPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false, length = 50)
    private String name; // e.g., "January 2025", "Q1 2025"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PeriodType periodType; // DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.OPEN;

    @Column
    private LocalDateTime closedAt;

    @Column(length = 100)
    private String closedBy;

    @Column(length = 1000)
    private String closingNotes;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum PeriodType {
        DAILY,
        WEEKLY,
        MONTHLY,
        QUARTERLY,
        YEARLY,
        CUSTOM
    }

    public enum Status {
        OPEN,       // Period is active, transactions can be posted
        CLOSED,     // Period is closed, no more transactions allowed
        LOCKED      // Period is locked for audit, cannot be reopened
    }

    /**
     * Check if this period contains the given date
     */
    public boolean containsDate(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    /**
     * Check if period is currently active
     */
    public boolean isActive() {
        LocalDate today = LocalDate.now();
        return status == Status.OPEN && containsDate(today);
    }
}
