package com.elcafe.modules.waiter.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Tracks waiter-table assignments over time
 * Maintains history of which waiter served which table and when
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@jakarta.persistence.Table(name = "waiter_tables", indexes = {
    @Index(name = "idx_waiter_active", columnList = "waiter_id,active"),
    @Index(name = "idx_table_active", columnList = "table_id,active")
})
@EntityListeners(AuditingEntityListener.class)
public class WaiterTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "waiter_id", nullable = false)
    @JsonIgnore
    private Waiter waiter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id", nullable = false)
    @JsonIgnore
    private Table table;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    @Column
    private LocalDateTime unassignedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    /**
     * Unassign the waiter from the table
     */
    public void unassign() {
        this.active = false;
        this.unassignedAt = LocalDateTime.now();
    }
}
