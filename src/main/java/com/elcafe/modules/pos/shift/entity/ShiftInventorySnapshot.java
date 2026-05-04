package com.elcafe.modules.pos.shift.entity;

import com.elcafe.modules.inventory.entity.Ingredient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Entity
@Table(name = "shift_inventory_snapshots", indexes = {
        @Index(name = "idx_shift_inv_snapshot_shift", columnList = "shift_id, snapshot_type"),
        @Index(name = "idx_shift_inv_snapshot_ingredient", columnList = "ingredient_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShiftInventorySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false)
    private EmployeeShift shift;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_type", nullable = false, length = 10)
    private SnapshotType snapshotType;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(name = "expected_quantity", precision = 10, scale = 3)
    private BigDecimal expectedQuantity;

    @Column(precision = 10, scale = 3)
    private BigDecimal variance;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum SnapshotType {
        START,
        END
    }
}
