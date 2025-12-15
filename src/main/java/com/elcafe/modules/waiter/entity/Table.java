package com.elcafe.modules.waiter.entity;

import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.waiter.enums.TableStatus;
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
 * Represents a physical table in the restaurant
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@jakarta.persistence.Table(name = "tables", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"restaurant_id", "number"})
})
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "orders", "waiterTables"})
public class Table {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false)
    private Integer number;

    @Column(nullable = false)
    @Builder.Default
    private Integer capacity = 4;

    @Column(length = 50)
    private String floor;

    @Column(length = 50)
    private String section;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private TableStatus status = TableStatus.FREE;

    /**
     * Current waiter assigned to this table
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_waiter_id")
    private Waiter currentWaiter;

    /**
     * Reference to another table this table is merged with
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merged_with_id")
    private Table mergedWith;

    /**
     * Tables merged into this one
     */
    @OneToMany(mappedBy = "mergedWith")
    @Builder.Default
    private List<Table> mergedTables = new ArrayList<>();

    @Column
    private LocalDateTime openedAt;

    @Column
    private LocalDateTime closedAt;

    @OneToMany(mappedBy = "table")
    @Builder.Default
    private List<Order> orders = new ArrayList<>();

    @OneToMany(mappedBy = "table", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WaiterTable> waiterTables = new ArrayList<>();

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Check if table is available for customers
     */
    public boolean isAvailable() {
        return status == TableStatus.FREE && mergedWith == null;
    }

    /**
     * Check if table is merged with another table
     */
    public boolean isMerged() {
        return mergedWith != null;
    }

    /**
     * Get the main table if this is merged, otherwise return self
     */
    public Table getMainTable() {
        return mergedWith != null ? mergedWith : this;
    }

    /**
     * Add an order to this table
     */
    public void addOrder(Order order) {
        orders.add(order);
        order.setTable(this);
    }

    /**
     * Add waiter-table assignment
     */
    public void addWaiterTable(WaiterTable waiterTable) {
        waiterTables.add(waiterTable);
        waiterTable.setTable(this);
    }
}
