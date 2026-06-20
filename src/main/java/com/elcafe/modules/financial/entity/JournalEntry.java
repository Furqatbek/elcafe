package com.elcafe.modules.financial.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity(name = "FinancialJournalEntry")
@Table(name = "financial_journal_entries", indexes = {
        @Index(name = "idx_journal_entry_restaurant", columnList = "restaurant_id"),
        @Index(name = "idx_journal_entry_date", columnList = "entry_date"),
        @Index(name = "idx_journal_entry_reference", columnList = "reference_type, reference_id")
})
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false, length = 50)
    private String entryNumber; // Unique entry number like "JE-2025-001"

    @Column(nullable = false)
    private LocalDate entryDate;

    @Column(length = 500)
    private String description;

    @Column(length = 100)
    private String referenceType; // ORDER, PURCHASE_ORDER, EXPENSE, PAYROLL, etc.

    @Column
    private Long referenceId;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalDebit;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal totalCredit;

    @Column(nullable = false)
    @Builder.Default
    private Boolean balanced = false; // True if totalDebit == totalCredit

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.DRAFT;

    @Column(length = 100)
    private String createdBy;

    @Column(length = 100)
    private String approvedBy;

    @Column
    private LocalDateTime approvedAt;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @JsonIgnore
    private List<Transaction> transactions = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum Status {
        DRAFT,
        POSTED,
        REVERSED,
        VOID
    }

    /**
     * Add a transaction to this journal entry
     */
    public void addTransaction(Transaction transaction) {
        transactions.add(transaction);
        transaction.setJournalEntry(this);
        recalculateTotals();
    }

    /**
     * Recalculate total debits and credits
     */
    public void recalculateTotals() {
        totalDebit = transactions.stream()
                .filter(t -> t.getType() == Transaction.TransactionType.DEBIT)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        totalCredit = transactions.stream()
                .filter(t -> t.getType() == Transaction.TransactionType.CREDIT)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        balanced = totalDebit.compareTo(totalCredit) == 0;
    }

    /**
     * Check if entry is balanced (debits = credits)
     */
    public boolean isBalanced() {
        return totalDebit != null && totalCredit != null &&
               totalDebit.compareTo(totalCredit) == 0;
    }
}
