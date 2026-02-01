package com.elcafe.modules.financial.entity;

import com.elcafe.modules.restaurant.entity.Restaurant;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity(name = "FinancialAccount")
@Table(name = "financial_accounts", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"restaurant_id", "code"})
})
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    @JsonIgnore
    private Restaurant restaurant;

    @Column(nullable = false, length = 50)
    private String code; // e.g., "1000", "2000", "3000", "4000", "5000"

    @Column(nullable = false, length = 200)
    private String name; // e.g., "Cash", "Accounts Payable", "Sales Revenue"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AccountType type; // ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AccountCategory category; // CASH, INVENTORY, COGS, SALES, LABOR, etc.

    @Column(length = 500)
    private String description;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NormalBalance normalBalance; // DEBIT or CREDIT

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_account_id")
    @JsonIgnore
    private Account parentAccount;

    @JsonProperty("restaurantId")
    public Long getRestaurantId() {
        return restaurant != null ? restaurant.getId() : null;
    }

    @JsonProperty("parentAccountId")
    public Long getParentAccountId() {
        return parentAccount != null ? parentAccount.getId() : null;
    }

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean systemAccount = false; // System accounts cannot be deleted

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
     * Check if this account has been soft-deleted.
     */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Soft delete this account. Financial records should never be hard deleted.
     */
    public void softDelete(String deletedByUser) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedByUser;
    }

    /**
     * Restore a soft-deleted account.
     */
    public void restore() {
        this.deletedAt = null;
        this.deletedBy = null;
    }

    public enum AccountType {
        ASSET,      // Things the business owns (cash, inventory, equipment)
        LIABILITY,  // Things the business owes (accounts payable, loans)
        EQUITY,     // Owner's stake in the business
        REVENUE,    // Money earned from sales
        EXPENSE     // Costs of doing business
    }

    public enum AccountCategory {
        // Assets
        CASH,
        BANK,
        INVENTORY,
        EQUIPMENT,
        ACCOUNTS_RECEIVABLE,

        // Liabilities
        ACCOUNTS_PAYABLE,
        LOANS,
        TAXES_PAYABLE,

        // Equity
        OWNER_EQUITY,
        RETAINED_EARNINGS,

        // Revenue
        SALES,
        DELIVERY_FEES,
        SERVICE_FEES,
        OTHER_REVENUE,

        // Contra-Revenue (reduces revenue)
        SALES_DISCOUNTS,      // Coupon, promotion, manual discounts
        SALES_RETURNS,        // Returns and refunds

        // Expenses
        COGS,           // Cost of Goods Sold
        LABOR,          // Staff wages
        RENT,
        UTILITIES,
        SUPPLIES,
        MARKETING,
        DELIVERY_COSTS,
        OTHER_EXPENSE
    }

    public enum NormalBalance {
        DEBIT,   // Assets and Expenses increase with debits
        CREDIT   // Liabilities, Equity, and Revenue increase with credits
    }

    /**
     * Update account balance based on transaction amount and type
     */
    public void updateBalance(BigDecimal amount, TransactionType transactionType) {
        if (transactionType == TransactionType.DEBIT) {
            if (normalBalance == NormalBalance.DEBIT) {
                balance = balance.add(amount);
            } else {
                balance = balance.subtract(amount);
            }
        } else { // CREDIT
            if (normalBalance == NormalBalance.CREDIT) {
                balance = balance.add(amount);
            } else {
                balance = balance.subtract(amount);
            }
        }
    }

    public enum TransactionType {
        DEBIT,
        CREDIT
    }
}
