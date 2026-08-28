package com.elcafe.modules.pos.shift.entity;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.financial.entity.Expense;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.waiter.entity.Waiter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "employee_consumptions")
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class EmployeeConsumption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_shift_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private EmployeeShift employeeShift;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "waiter_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Waiter waiter;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "resetToken", "resetTokenExpiry"})
    private User employee;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Product product;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 1;

    // NOTE: despite their names, costPrice/totalCost hold the RETAIL value
    // (selling price) of the consumption. They drive the allowance money-limit
    // logic in ConsumptionLimitService, which prices limits at retail, so they
    // must stay retail. Actual COGS lives in actualUnitCost/actualTotalCost
    // below and is what the company expense and cost reports use.
    @Column(name = "cost_price", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal costPrice = BigDecimal.ZERO;

    @Column(name = "total_cost", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalCost = BigDecimal.ZERO;

    /** Actual per-unit cost (COGS) captured at consumption time. */
    @Column(name = "actual_unit_cost", precision = 10, scale = 2)
    private BigDecimal actualUnitCost;

    /** Actual total cost (COGS) = actualUnitCost × quantity. */
    @Column(name = "actual_total_cost", precision = 10, scale = 2)
    private BigDecimal actualTotalCost;

    /**
     * True when part or all of this consumption was over the employee's
     * configured allowance and therefore charged back via a PayrollEntry
     * advance. False (the default) means the company absorbed the cost.
     */
    @Column(name = "charged_to_employee", nullable = false)
    @Builder.Default
    private Boolean chargedToEmployee = false;

    /**
     * Portion of totalCost that was billed to the employee as a salary
     * advance. Mirrors the netPay on the matching ADVANCE PayrollEntry.
     */
    @Column(name = "charged_amount", precision = 12, scale = 2)
    private BigDecimal chargedAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expense_id")
    private Expense expense;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "consumed_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    @Builder.Default
    private OffsetDateTime consumedAt = OffsetDateTime.now();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    public String getConsumerName() {
        if (waiter != null) return waiter.getName();
        if (employee != null) return employee.getFullName();
        return "Unknown";
    }

    /**
     * The actual cost (COGS) of this consumption for cost reports. Prefers the
     * value captured at consumption time; for rows written before that column
     * existed it falls back to the product's current cost, then to the retail
     * total as a last resort. (product is EAGER, so this is session-safe.)
     */
    public BigDecimal getEffectiveTotalCost() {
        if (actualTotalCost != null) return actualTotalCost;
        if (product != null && product.getCostPrice() != null) {
            return product.getCostPrice().multiply(BigDecimal.valueOf(quantity != null ? quantity : 0));
        }
        return totalCost != null ? totalCost : BigDecimal.ZERO;
    }
}
