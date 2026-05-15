package com.elcafe.modules.pos.shift.entity;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.pos.cashdrawer.entity.CashDrawer;
import com.elcafe.modules.pos.shift.enums.ShiftStatus;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.waiter.entity.Waiter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Employee shift for time tracking, clock-in/out, and end-of-day reconciliation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "employee_shifts", indexes = {
    @Index(name = "idx_employee_shifts_restaurant", columnList = "restaurant_id"),
    @Index(name = "idx_employee_shifts_employee", columnList = "employee_id"),
    @Index(name = "idx_employee_shifts_date", columnList = "shift_date"),
    @Index(name = "idx_employee_shifts_status", columnList = "status")
})
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class EmployeeShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "waiter_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Waiter waiter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_drawer_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private CashDrawer cashDrawer;

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    @Column(name = "clock_in", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime clockIn;

    @Column(name = "clock_out", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime clockOut;

    @Column(name = "scheduled_start")
    private LocalTime scheduledStart;

    @Column(name = "scheduled_end")
    private LocalTime scheduledEnd;

    @Column(name = "break_minutes")
    @Builder.Default
    private Integer breakMinutes = 0;

    @Column(name = "overtime_minutes")
    @Builder.Default
    private Integer overtimeMinutes = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ShiftStatus status = ShiftStatus.ACTIVE;

    // Opening counts
    @Column(name = "opening_cash", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal openingCash = BigDecimal.ZERO;

    // Closing counts
    @Column(name = "closing_cash", precision = 10, scale = 2)
    private BigDecimal closingCash;

    @Column(name = "expected_cash", precision = 10, scale = 2)
    private BigDecimal expectedCash;

    @Column(name = "cash_variance", precision = 10, scale = 2)
    private BigDecimal cashVariance;

    // Shift totals
    @Column(name = "total_sales", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalSales = BigDecimal.ZERO;

    @Column(name = "total_cash_sales", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalCashSales = BigDecimal.ZERO;

    @Column(name = "total_card_sales", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalCardSales = BigDecimal.ZERO;

    @Column(name = "total_tips", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalTips = BigDecimal.ZERO;

    @Column(name = "total_orders")
    @Builder.Default
    private Integer totalOrders = 0;

    @Column(name = "total_refunds", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalRefunds = BigDecimal.ZERO;

    @Column(name = "total_voids", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalVoids = BigDecimal.ZERO;

    /**
     * Marks the shift as already settled for payroll purposes. Set by the
     * fire-on-clock-out hook for PER_SHIFT salary configs and by the daily
     * batcher for DAILY / PER_SHIFT configs that get paid via the cron.
     * Stops both code paths from double-paying the same shift.
     */
    @Column(name = "paid_for_salary", nullable = false)
    @Builder.Default
    private Boolean paidForSalary = false;

    // Approvals
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private User approvedBy;

    @Column(name = "approved_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime approvedAt;

    @Column(name = "manager_notes", columnDefinition = "TEXT")
    private String managerNotes;

    @Column(name = "employee_notes", columnDefinition = "TEXT")
    private String employeeNotes;

    @OneToMany(mappedBy = "shift", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ShiftBreak> breaks = new ArrayList<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    // Helper methods

    public void clockOut() {
        this.clockOut = OffsetDateTime.now();
        this.status = ShiftStatus.APPROVED;
        calculateTotalBreakMinutes();
    }

    public void startBreak() {
        ShiftBreak shiftBreak = ShiftBreak.builder()
            .shift(this)
            .breakStart(OffsetDateTime.now())
            .build();
        this.breaks.add(shiftBreak);
    }

    public void endBreak() {
        if (!breaks.isEmpty()) {
            ShiftBreak currentBreak = breaks.get(breaks.size() - 1);
            if (currentBreak.getBreakEnd() == null) {
                currentBreak.setBreakEnd(OffsetDateTime.now());
            }
        }
        calculateTotalBreakMinutes();
    }

    private void calculateTotalBreakMinutes() {
        this.breakMinutes = breaks.stream()
            .filter(b -> b.getBreakEnd() != null)
            .mapToInt(b -> (int) Duration.between(b.getBreakStart(), b.getBreakEnd()).toMinutes())
            .sum();
    }

    public long getWorkedMinutes() {
        if (clockOut == null) {
            return Duration.between(clockIn, OffsetDateTime.now()).toMinutes() - (breakMinutes != null ? breakMinutes : 0);
        }
        return Duration.between(clockIn, clockOut).toMinutes() - (breakMinutes != null ? breakMinutes : 0);
    }

    public BigDecimal getWorkedHours() {
        return BigDecimal.valueOf(getWorkedMinutes() / 60.0);
    }

    public void calculateExpectedCash() {
        this.expectedCash = openingCash
            .add(totalCashSales != null ? totalCashSales : BigDecimal.ZERO)
            .subtract(totalRefunds != null ? totalRefunds : BigDecimal.ZERO);
    }

    public void reconcile(BigDecimal actualCash) {
        this.closingCash = actualCash;
        calculateExpectedCash();
        this.cashVariance = actualCash.subtract(expectedCash);
    }

    public void approve(User manager, String notes) {
        this.approvedBy = manager;
        this.approvedAt = OffsetDateTime.now();
        this.managerNotes = notes;
        this.status = ShiftStatus.APPROVED;
    }
}
