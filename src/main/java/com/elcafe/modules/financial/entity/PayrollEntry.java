package com.elcafe.modules.financial.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.waiter.entity.Waiter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity(name = "FinancialPayrollEntry")
@Table(name = "financial_payroll_entries", indexes = {
        @Index(name = "idx_payroll_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_payroll_employee", columnList = "employee_id"),
        @Index(name = "idx_payroll_period", columnList = "pay_period_start, pay_period_end"),
        @Index(name = "idx_payroll_date", columnList = "payment_date")
})
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayrollEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "resetToken", "resetTokenExpiry"})
    private User employee;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "waiter_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Waiter waiter;

    @Column(nullable = false, length = 50)
    private String payrollNumber; // e.g., "PAY-2025-001"

    @Column(nullable = false)
    private LocalDate payPeriodStart;

    @Column(nullable = false)
    private LocalDate payPeriodEnd;

    @Column
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayrollType payrollType;

    @Column(precision = 10, scale = 2)
    private BigDecimal hoursWorked;

    @Column(precision = 10, scale = 2)
    private BigDecimal hourlyRate;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal baseSalary = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal overtimePay = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal bonus = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal tips = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal commission = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal grossPay = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal taxDeduction = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal socialSecurityDeduction = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal healthInsuranceDeduction = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal otherDeductions = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal netPay = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private PaymentMethod paymentMethod;

    @Column(length = 1000)
    private String notes;

    @Column(length = 100)
    private String processedBy;

    @Column
    private LocalDateTime processedAt;

    @Column(length = 100)
    private String approvedBy;

    @Column
    private LocalDateTime approvedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Soft delete support - financial records should never be hard deleted
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by", length = 100)
    private String deletedBy;

    // ==================== SOFT DELETE METHODS ====================

    /**
     * Check if this payroll entry has been soft-deleted.
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Soft delete this payroll entry. Financial records should never be hard deleted.
     */
    public void softDelete(String deletedByUser) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedByUser;
    }

    /**
     * Restore a soft-deleted payroll entry.
     */
    public void restore() {
        this.deletedAt = null;
        this.deletedBy = null;
    }

    public enum PayrollType {
        HOURLY,
        SALARY,
        CONTRACT,
        COMMISSION,
        ADVANCE
    }

    public enum PaymentStatus {
        PENDING,
        APPROVED,
        PAID,
        CANCELLED
    }

    public enum PaymentMethod {
        CASH,
        BANK_TRANSFER,
        CHECK,
        DIRECT_DEPOSIT
    }

    /**
     * Calculate gross pay based on type and components
     */
    public void calculateGrossPay() {
        grossPay = BigDecimal.ZERO;

        if (payrollType == PayrollType.HOURLY && hoursWorked != null && hourlyRate != null) {
            grossPay = hoursWorked.multiply(hourlyRate);
        } else if (baseSalary != null) {
            grossPay = baseSalary;
        }

        grossPay = grossPay
                .add(overtimePay != null ? overtimePay : BigDecimal.ZERO)
                .add(bonus != null ? bonus : BigDecimal.ZERO)
                .add(tips != null ? tips : BigDecimal.ZERO)
                .add(commission != null ? commission : BigDecimal.ZERO);
    }

    /**
     * Calculate total deductions and net pay
     */
    @PrePersist
    @PreUpdate
    public void calculateNetPay() {
        calculateGrossPay();

        totalDeductions = (taxDeduction != null ? taxDeduction : BigDecimal.ZERO)
                .add(socialSecurityDeduction != null ? socialSecurityDeduction : BigDecimal.ZERO)
                .add(healthInsuranceDeduction != null ? healthInsuranceDeduction : BigDecimal.ZERO)
                .add(otherDeductions != null ? otherDeductions : BigDecimal.ZERO);

        netPay = grossPay.subtract(totalDeductions);
    }
}
