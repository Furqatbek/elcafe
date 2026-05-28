package com.elcafe.modules.financial.entity;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.waiter.entity.Waiter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "salary_configs")
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SalaryConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "resetToken", "resetTokenExpiry"})
    private User employee;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "waiter_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Waiter waiter;

    @Column(name = "monthly_salary", precision = 12, scale = 2)
    private BigDecimal monthlySalary;

    /**
     * Canonical pay amount per period. Semantics depend on payFrequency:
     *   MONTHLY    — total amount for one month (mirrors monthlySalary)
     *   WEEKLY     — total amount for one week
     *   BIWEEKLY   — total amount for two weeks
     *   DAILY      — amount per working day
     *   PER_SHIFT  — amount per shift worked
     *   HOURLY     — amount per hour clocked
     * Legacy MONTHLY rows are backfilled from monthlySalary by V131.
     */
    @Column(name = "base_amount", precision = 12, scale = 2)
    private BigDecimal baseAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_frequency", nullable = false, length = 20)
    @Builder.Default
    private PayFrequency payFrequency = PayFrequency.MONTHLY;

    /**
     * Day of the month the salary is auto-paid (1..28). Used only for
     * MONTHLY configs; ignored for DAILY / PER_SHIFT / HOURLY.
     */
    @Column(name = "pay_day")
    private Integer payDay;

    /**
     * Day of the week the salary is auto-paid (1=Monday..7=Sunday). Used
     * only for WEEKLY / BIWEEKLY configs; ignored otherwise. V135 widens
     * the underlying column to INTEGER so this plain JPA mapping
     * validates cleanly.
     */
    @Column(name = "pay_day_of_week")
    private Integer payDayOfWeek;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    @Builder.Default
    private PayrollEntry.PaymentMethod paymentMethod = PayrollEntry.PaymentMethod.CASH;

    @Column(name = "auto_approve")
    @Builder.Default
    private Boolean autoApprove = true;

    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    @Column(name = "last_paid_date")
    private LocalDate lastPaidDate;

    /**
     * Minutes of grace past the shift's scheduledStart before a clock-in
     * counts as late. Only consulted for HOURLY configs today. Defaults
     * to 5 minutes so an unset value matches the documented norm.
     */
    @Column(name = "late_grace_minutes", nullable = false)
    @Builder.Default
    private Integer lateGraceMinutes = 5;

    /**
     * Fixed cash deduction added to that period's payroll deductions for
     * each shift the employee clocked in late (beyond the grace window).
     * Zero disables the punishment.
     */
    @Column(name = "late_penalty_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal latePenaltyAmount = BigDecimal.ZERO;

    /**
     * Extra cash deduction per <em>started</em> hour the employee is
     * late, on top of {@link #latePenaltyAmount}. So a 5-minute grace
     * plus a 6-minute-late clock-in (1 minute past grace) charges one
     * full hour's worth. Set both fields to combine a flat strike fine
     * with hourly bleed, or leave one at zero to use only the other.
     */
    @Column(name = "late_penalty_per_hour", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal latePenaltyPerHour = BigDecimal.ZERO;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    /**
     * Returns the canonical pay amount per period, falling back to
     * monthlySalary for legacy rows that pre-date base_amount.
     */
    public BigDecimal effectiveBaseAmount() {
        if (baseAmount != null) return baseAmount;
        if (monthlySalary != null) return monthlySalary;
        return BigDecimal.ZERO;
    }

    public enum PayFrequency {
        DAILY,
        WEEKLY,
        BIWEEKLY,
        MONTHLY,
        PER_SHIFT,
        HOURLY
    }
}
