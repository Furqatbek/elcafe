package com.elcafe.modules.financial.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity(name = "FinancialTransaction")
@Table(name = "financial_transactions", indexes = {
        @Index(name = "idx_financial_transaction_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_financial_transaction_date", columnList = "transaction_date"),
        @Index(name = "idx_financial_transaction_account", columnList = "account_id"),
        @Index(name = "idx_financial_transaction_reference", columnList = "reference_type, reference_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false)
    private LocalDate transactionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type; // DEBIT or CREDIT

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(precision = 15, scale = 2)
    private BigDecimal balanceBefore;

    @Column(precision = 15, scale = 2)
    private BigDecimal balanceAfter;

    @Column(length = 100)
    private String referenceType; // ORDER, PURCHASE_ORDER, EXPENSE, PAYROLL, INVENTORY_ADJUSTMENT

    @Column
    private Long referenceId;

    @Column(length = 500)
    private String description;

    @Column(length = 1000)
    private String notes;

    @Column(length = 100)
    private String performedBy; // Username or system

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id")
    private JournalEntry journalEntry; // Link to double-entry journal

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum TransactionType {
        DEBIT,
        CREDIT
    }

    public enum ReferenceType {
        ORDER,
        PURCHASE_ORDER,
        EXPENSE,
        PAYROLL,
        INVENTORY_ADJUSTMENT,
        MANUAL_ADJUSTMENT,
        REFUND,
        DELIVERY_FEE
    }
}
