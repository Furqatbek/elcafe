package com.elcafe.modules.waiter.entity;

import com.elcafe.modules.waiter.enums.WaiterRole;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

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

    @Column(nullable = false, unique = true, length = 6)
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

    @OneToMany(mappedBy = "waiter", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
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
