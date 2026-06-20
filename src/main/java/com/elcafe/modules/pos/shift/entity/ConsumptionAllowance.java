package com.elcafe.modules.pos.shift.entity;

import com.elcafe.modules.auth.entity.User;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.waiter.entity.Waiter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "consumption_allowances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Filter(name = "restaurantFilter", condition = "restaurant_id = :restaurantId")
public class ConsumptionAllowance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "resetToken", "resetTokenExpiry"})
    private User employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "waiter_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Waiter waiter;

    /**
     * Optional role-scope: when non-null the rule applies to every
     * employee or waiter whose role string matches this value
     * (case-insensitive). employee_id, waiter_id and role are
     * mutually exclusive — the DB check constraint guarantees it.
     */
    @Column(length = 50)
    private String role;

    /**
     * When false the over-limit overflow is recorded on the consumption
     * row but no PayrollEntry advance is posted automatically — the
     * operator handles billing manually. Defaults true.
     */
    @Column(name = "bill_overflow", nullable = false)
    @Builder.Default
    private Boolean billOverflow = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Period period;

    @Column(name = "limit_count")
    private Integer limitCount;

    @Column(name = "limit_amount", precision = 12, scale = 2)
    private BigDecimal limitAmount;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    /**
     * Score the rule's specificity for tie-breaking at lookup time.
     * Higher = wins: an exact subject is more specific than a role
     * scope, and a category-specific rule beats an any-category one
     * at the same subject level.
     */
    public int specificity() {
        int subjectScore;
        if (employee != null || waiter != null) subjectScore = 4;
        else if (role != null && !role.isBlank()) subjectScore = 2;
        else subjectScore = 0;
        int categoryScore = category != null ? 1 : 0;
        return subjectScore + categoryScore;
    }

    public enum Period {
        PER_SHIFT,
        DAILY,
        WEEKLY,
        MONTHLY
    }
}
