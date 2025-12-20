package com.elcafe.modules.financial.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity(name = "FinancialExpense")
@Table(name = "financial_expenses", indexes = {
        @Index(name = "idx_expense_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_expense_date", columnList = "expense_date"),
        @Index(name = "idx_expense_category", columnList = "category"),
        @Index(name = "idx_expense_account", columnList = "account_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account; // Which expense account to charge

    @Column(nullable = false, length = 50)
    private String expenseNumber; // e.g., "EXP-2025-001"

    @Column(nullable = false)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ExpenseCategory category;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(length = 200)
    private String vendor;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    @Column
    private LocalDate paymentDate;

    @Column(length = 100)
    private String referenceNumber; // Invoice number, receipt number, etc.

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    @Builder.Default
    private Boolean recurring = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private RecurringPeriod recurringPeriod;

    @Column(length = 500)
    private String attachmentUrl; // Receipt/invoice attachment

    @Column(length = 100)
    private String createdBy;

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

    public enum ExpenseCategory {
        RENT,
        UTILITIES,
        SUPPLIES,
        MARKETING,
        INSURANCE,
        MAINTENANCE,
        EQUIPMENT,
        LICENSES,
        TAXES,
        DELIVERY_COSTS,
        PROFESSIONAL_FEES,
        BANK_FEES,
        OTHER
    }

    public enum PaymentMethod {
        CASH,
        CARD,
        BANK_TRANSFER,
        CHECK,
        OTHER
    }

    public enum PaymentStatus {
        UNPAID,
        PAID,
        PARTIALLY_PAID,
        OVERDUE
    }

    public enum RecurringPeriod {
        WEEKLY,
        MONTHLY,
        QUARTERLY,
        YEARLY
    }

    /**
     * Calculate total amount including tax and initialize defaults
     */
    @PrePersist
    @PreUpdate
    public void calculateTotal() {
        // Initialize defaults
        if (taxAmount == null) {
            taxAmount = BigDecimal.ZERO;
        }
        if (recurring == null) {
            recurring = false;
        }
        if (paymentMethod == null) {
            paymentMethod = PaymentMethod.CASH;
        }
        if (paymentStatus == null) {
            paymentStatus = PaymentStatus.UNPAID;
        }

        // Calculate total
        if (amount != null) {
            totalAmount = amount.add(taxAmount);
        } else if (totalAmount == null) {
            totalAmount = BigDecimal.ZERO;
        }
    }
}
