package com.elcafe.modules.order.entity;

import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Join entity representing the many-to-many relationship between Orders and RestaurantTables.
 * This properly models multi-table orders where a single order can span multiple tables.
 */
@Entity
@Table(name = "order_tables",
       uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "table_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id", nullable = false)
    private RestaurantTable table;

    /**
     * Whether this is the primary table for the order.
     * The primary table is used for display purposes and backward compatibility.
     */
    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private Boolean isPrimary = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
