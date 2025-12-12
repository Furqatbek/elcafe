package com.elcafe.modules.inventory.entity;

import com.elcafe.modules.inventory.enums.TransactionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inventory_transactions")
@EntityListeners(AuditingEntityListener.class)
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TransactionType type;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal balanceBefore;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal balanceAfter;

    @Column(length = 50)
    private String referenceType; // ORDER, PURCHASE, ADJUSTMENT, WASTE, etc.

    @Column
    private Long referenceId; // Order ID, Purchase ID, etc.

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(length = 100)
    private String performedBy;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
