package com.elcafe.modules.waiter.entity;

import com.elcafe.modules.waiter.enums.CommissionType;
import com.elcafe.modules.waiter.enums.WaiterRole;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a waiter/server in the restaurant
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@jakarta.persistence.Table(name = "waiters")
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "waiterTables"})
public class Waiter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 10)
    private String pinCode;

    @Column(unique = true, length = 100)
    private String email;

    @Column(length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private WaiterRole role = WaiterRole.WAITER;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * JSON array of permissions
     * Example: ["MANAGE_TABLES", "OVERRIDE_PRICES", "VOID_ITEMS", "MERGE_TABLES"]
     */
    @Column(columnDefinition = "TEXT")
    private String permissions;

    /**
     * Commission percentage for this waiter (0-100)
     * Example: 5.00 means 5% commission on order total
     */
    @Column(precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal commissionPercent = BigDecimal.ZERO;

    /**
     * Whether commission tracking is enabled for this waiter
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean commissionEnabled = false;

    /**
     * Type of commission calculation (PERCENTAGE or FIXED_AMOUNT)
     * Default is PERCENTAGE for backward compatibility
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private CommissionType commissionType = CommissionType.PERCENTAGE;

    /**
     * Fixed commission amount per order (used when commissionType is FIXED_AMOUNT)
     */
    @Column(precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal fixedCommissionAmount = BigDecimal.ZERO;

    @OneToMany(mappedBy = "waiter", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<WaiterTable> waiterTables = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Check if waiter can manage a specific table
     */
    public boolean canManageTable(Long tableId) {
        if (!active) {
            return false;
        }

        // Head waiters and supervisors can manage any table
        if (role == WaiterRole.HEAD_WAITER || role == WaiterRole.SUPERVISOR) {
            return true;
        }

        // Check if waiter is assigned to this table
        return waiterTables.stream()
                .anyMatch(wt -> wt.getTable().getId().equals(tableId) && wt.getActive());
    }

    /**
     * Check if waiter has a specific permission
     */
    public boolean hasPermission(String permission) {
        if (!active) {
            return false;
        }

        // Supervisors have all permissions
        if (role == WaiterRole.SUPERVISOR) {
            return true;
        }

        if (permissions == null || permissions.isEmpty()) {
            return false;
        }

        return permissions.contains(permission);
    }

    /**
     * Add waiter-table assignment
     */
    public void addWaiterTable(WaiterTable waiterTable) {
        waiterTables.add(waiterTable);
        waiterTable.setWaiter(this);
    }
}
